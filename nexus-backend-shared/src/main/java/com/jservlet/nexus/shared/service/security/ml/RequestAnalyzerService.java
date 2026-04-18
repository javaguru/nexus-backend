/*
 * Copyright (C) 2001-2026 JServlet.com Franck Andriano.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.jservlet.nexus.shared.service.security.ml;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.Map;

/**
 * Analyzer Request Service with Matrix Batching and Dynamic Context-Preserving Chunking.<br>
 * The DistilBERT Model with HuggingFace Tokenizer in an ONNX Neural Network Environment.
 */
public class RequestAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(RequestAnalyzerService.class);

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    @Value("${nexus.api.backend.analyzer.onnx.maxLength:512}")
    private String maxLength;
    @Value("${nexus.api.backend.analyzer.onnx.truncation:false}") // Must be false so we handle chunking ourselves
    private boolean truncation;

    @Value("${nexus.api.backend.analyzer.onnx.path.model:model/model.onnx}")
    private String pathModel;
    @Value("${nexus.api.backend.analyzer.onnx.path.tokenizer:model/tokenizer.json}")
    private String pathTokenizer;

    @Value("${nexus.api.backend.analyzer.onnx.cpu:4}")
    private int cpu;

    @PostConstruct
    public void init() throws Exception {
        Map<String, String> optionsHuggingFace = Map.of(
                "maxLength", maxLength, // input token length maw (!?)
                "truncation", String.valueOf(truncation)
        );
        logger.info("Starting RequestAnalyzer Neural Network AI: {}", optionsHuggingFace);

        // Initialize Tokenizer
        try (InputStream tokenizerStream = new ClassPathResource(pathTokenizer).getInputStream()) {
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerStream, optionsHuggingFace);
        }

        // Initialize environment ONNX
        this.env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions optionsSession = new OrtSession.SessionOptions();
        // Maximize CPU performance for ONNX
        optionsSession.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        // Avoid thread contention with Tomcat (1 or 2 threads per inference is usually best for high-throughput web apps)
        optionsSession.setIntraOpNumThreads(cpu);
        // Stealth mode, no internal timing
        optionsSession.disableProfiling();

        // Load the model
        try (InputStream modelStream = new ClassPathResource(pathModel).getInputStream()) {
            // readAllBytes() est parfait ici car tu es sur Java 21 !
            byte[] modelBytes = modelStream.readAllBytes();
            this.session = env.createSession(modelBytes, optionsSession);
        }
        logger.info("ONNX model ready. Inputs: {}", session.getInputNames());
    }

    /**
     * Analyze content text with Matrix Batching and Dynamic Context-Preserving Chunking.
     */
    public boolean isMalicious(String requestPayload) throws Exception {
        long startTime = System.currentTimeMillis();

        // Tokenize initial
        Encoding encoding = tokenizer.encode(requestPayload);
        long[] allInputIds = encoding.getIds();
        long[] allAttentionMask = encoding.getAttentionMask();

        int totalTokens = allInputIds.length;
        int MAX_TOKENS = 512;

        // Dynamic prefix detection (HTTP context)
        int bodySeparatorIndex = requestPayload.indexOf("\nBODY:\n");
        int prefixTokensSize = Math.min(64, totalTokens);

        if (bodySeparatorIndex != -1) {
            String prefixStr = requestPayload.substring(0, bodySeparatorIndex + 7);
            Encoding prefixEncoding = tokenizer.encode(prefixStr);
            prefixTokensSize = Math.min(prefixEncoding.getIds().length, totalTokens);
            prefixTokensSize = Math.min(prefixTokensSize, 200);
        }

        // OPTIMIZED sliding window calculation
        int maxBodyTokensPerChunk = MAX_TOKENS - prefixTokensSize - 1;

        // Fixed overlap of 40 tokens instead of 50% (We're progressing much faster!)
        int overlap = 40;
        int stride = Math.max(1, maxBodyTokensPerChunk - overlap);

        int numChunks = 1;
        if (totalTokens > MAX_TOKENS) {
            int bodyTokensToProcess = totalTokens - prefixTokensSize;
            numChunks = (int) Math.ceil((double) bodyTokensToProcess / stride);
        }

        // Strict scan limit (CPU protection)
        // Scanning 3 chunks (~1500 tokens) is the sweet spot between security and performance
        int MAX_CHUNKS_TO_SCAN = 3;
        int chunksToProcess = Math.min(numChunks, MAX_CHUNKS_TO_SCAN);

        // SEQUENTIAL ASSESSMENT
        for (int i = 0; i < chunksToProcess; i++) {

            int bodyStart = prefixTokensSize + (i * stride);
            bodyStart = Math.min(bodyStart, totalTokens);

            int tokensRemaining = totalTokens - bodyStart;
            int copyLength = Math.min(maxBodyTokensPerChunk, tokensRemaining);

            int currentChunkLength;
            if (totalTokens > MAX_TOKENS && copyLength > 0) {
                currentChunkLength = prefixTokensSize + copyLength + 1; // +1 for the artificial [SEP]
            } else {
                currentChunkLength = totalTokens;
            }

            long[] chunkInputIds = new long[currentChunkLength];
            long[] chunkAttentionMask = new long[currentChunkLength];

            // Prefix Fill
            System.arraycopy(allInputIds, 0, chunkInputIds, 0, prefixTokensSize);
            System.arraycopy(allAttentionMask, 0, chunkAttentionMask, 0, prefixTokensSize);

            // Body filling for this chunk
            if (totalTokens > MAX_TOKENS && copyLength > 0) {
                System.arraycopy(allInputIds, bodyStart, chunkInputIds, prefixTokensSize, copyLength);
                System.arraycopy(allAttentionMask, bodyStart, chunkAttentionMask, prefixTokensSize, copyLength);
                // Force the [SEP] token at the end
                chunkInputIds[currentChunkLength - 1] = 102L;
                chunkAttentionMask[currentChunkLength - 1] = 1L;
            } else if (totalTokens <= MAX_TOKENS) {
                System.arraycopy(allInputIds, prefixTokensSize, chunkInputIds, prefixTokensSize, totalTokens - prefixTokensSize);
                System.arraycopy(allAttentionMask, prefixTokensSize, chunkAttentionMask, prefixTokensSize, totalTokens - prefixTokensSize);
            }

            // The tensor is sent individually (Size [1, X])
            long[] shape = new long[]{1, currentChunkLength};

            try (
                    OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(chunkInputIds), shape);
                    OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(chunkAttentionMask), shape)
            ) {
                Map<String, OnnxTensor> inputs = Map.of(
                        "input_ids", inputIdsTensor,
                        "attention_mask", attentionMaskTensor
                );

                try (OrtSession.Result results = session.run(inputs)) {
                    float[][] output = (float[][]) results.get(0).getValue();

                    // The output matrix is [1][2] (1 chunk, 2 probabilities)
                    float logitSafe = output[0][0];
                    float logitMalicious = output[0][1];

                    double expSafe = Math.exp(logitSafe);
                    double expMalicious = Math.exp(logitMalicious);
                    double attackProbability = expMalicious / (expSafe + expMalicious);

                    if (logger.isDebugEnabled()) {
                        logger.debug("WAF AI Score (Chunk {}/{}) - Safe: {}%, Attack: {}%",
                                i + 1, numChunks,
                                String.format("%.2f", (1.0 - attackProbability) * 100),
                                String.format("%.2f", attackProbability * 100));
                    }

                    if (attackProbability > 0.65) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Attack detected in chunk {} !", i + 1);
                        }
                        if (logger.isDebugEnabled()) {
                            long endTime = System.currentTimeMillis();
                            logger.debug("WAF Analysis Aborted early at chunk {}. Exec Time: {} ms", (i + 1), (endTime - startTime));
                        }
                        return true;
                    }
                }
            }
        }

        if (logger.isDebugEnabled()) {
            long endTime = System.currentTimeMillis();
            logger.debug("WAF Analysis Execution Time ({} chunks): {} ms", numChunks, (endTime - startTime));
        }
        return false;
    }

    @PreDestroy
    public void close() throws Exception {
        if (session != null) session.close();
        if (env != null) env.close();
        if (tokenizer != null) tokenizer.close();
    }
}
