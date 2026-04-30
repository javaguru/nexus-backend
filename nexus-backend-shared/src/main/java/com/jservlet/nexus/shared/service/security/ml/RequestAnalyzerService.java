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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.Map;

/**
 * AnalyzerRequestService analyze content text with a Context-Aware Sliding Window (Performance &lt;50 ms).<br>
 * <p>
 * Use DistilBERT Model ONNX Environment (Open Neural Network Exchange) with HuggingFace Tokenizer.<br>
 * <br>
 * Recommendations:<br>
 * - 4-6 Cpu max recommended<br>
 * - Strict limits to prevent DoS via massive payloads ~6500 Tokens maximum for 15 Chunks<br>
 * - Calibrated threshold:<br>
 *   0.50 = Very strict WAF (blocks at the slightest doubt)<br>
 *   0.65 = Balanced WAF (tolerates noise, blocks real attacks)<br>
 *   0.85 = Permissive WAF (only blocks absolutely obvious threats)<br>
 * </p>
 * Model ONNX INT8 Nexus v10.14_2
 *
 * @since version 2.0.0
 */
public class RequestAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(RequestAnalyzerService.class);

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    @Value("${nexus.api.backend.analyzer.onnx.maxLength:512}")
    private String maxLength;

    // Must be false so we handle chunking manually
    @Value("${nexus.api.backend.analyzer.onnx.truncation:false}")
    private boolean truncation;
    // Model ONNX
    @Value("${nexus.api.backend.analyzer.onnx.path.model:classpath:model/model.onnx}")
    private String pathModel;
    // Tokenizer JSON
    @Value("${nexus.api.backend.analyzer.onnx.path.tokenizer:classpath:model/tokenizer.json}")
    private String pathTokenizer;

    /*
     * 4-6 Cpu max recommended
     */
    @Value("${nexus.api.backend.analyzer.onnx.cpu:4}")
    private int CPU;

    /*
     * Strict limits to prevent DoS via massive payloads (~6500 Tokens maximum for 15 Chunks)
     */
    @Value("${nexus.api.backend.analyzer.onnx.max-chunks-to-scan:15}")
    private int MAX_CHUNKS_TO_SCAN;

    /*
     *  Calibrated threshold
     * - 0.50 = Very strict WAF (blocks at the slightest doubt)
     * - 0.65 = Balanced WAF (tolerates noise, blocks real attacks)
     * - 0.85 = Permissive WAF (only blocks absolutely obvious threats)
     */
    @Value("${nexus.api.backend.analyzer.onnx.attack.threshold:0.65}")
    private double THRESHOLD;

    private final ResourceLoader resourceLoader;

    public RequestAnalyzerService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() throws Exception {
        Map<String, String> optionsHuggingFace = Map.of(
                "maxLength", maxLength,
                "truncation", String.valueOf(truncation)
        );
        logger.info("Starting RequestAnalyzer Neural Network AI: {}", optionsHuggingFace);

        // Check Resource tokenizer
        Resource tokenizerResource = resourceLoader.getResource(pathTokenizer);
        if (!tokenizerResource.exists()) {
            throw new RuntimeException("Tokenizer file not found: " + pathTokenizer);
        }

        // Initialize Tokenizer
        try (InputStream tokenizerStream = tokenizerResource.getInputStream()) {
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerStream, optionsHuggingFace);
        }

        // Initialize environment ONNX
        this.env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions optionsSession = new OrtSession.SessionOptions();
        // Maximize CPU performance for ONNX
        optionsSession.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        // Avoid thread contention with Tomcat
        optionsSession.setIntraOpNumThreads(CPU);
        optionsSession.disableProfiling();

        // Check Resource Model
        Resource modelResource = resourceLoader.getResource(pathModel);
        if (!modelResource.exists()) {
            throw new RuntimeException("Model file not found: " + pathModel);
        }

        // Load the model
        try (InputStream modelStream = modelResource.getInputStream()) {
            byte[] modelBytes = modelStream.readAllBytes();
            this.session = env.createSession(modelBytes, optionsSession);
        }
        logger.info("ONNX model ready. Inputs: {}", session.getInputNames());
    }

    /**
     * Analyze content text with a pure Token Sliding Window while preserving Context.
     * [CLS] HEADERS [SEP] BODY PIECE [SEP]
     */
    public boolean isMalicious(String requestPayload) throws Exception {
        long startTime = System.currentTimeMillis();

        // One single encoding
        Encoding encoding = tokenizer.encode(requestPayload);
        long[] allInputIds = encoding.getIds();
        long[] allAttentionMask = encoding.getAttentionMask();

        int totalTokens = allInputIds.length;
        int MAX_TOKENS = Integer.parseInt(maxLength); // 512

        if (totalTokens <= MAX_TOKENS) {
            return processSingleChunk(allInputIds, allAttentionMask, 0, startTime);
        }

        // Find context boundary
        int headerCharLength = requestPayload.indexOf("\nBODY:\n");
        if (headerCharLength == -1) headerCharLength = requestPayload.length() / 2;

        int contextTokenEnd = (int) ((double) headerCharLength / requestPayload.length() * totalTokens) + 3;
        int MAX_CONTEXT_TOKENS = 150;
        if (contextTokenEnd > MAX_CONTEXT_TOKENS) contextTokenEnd = MAX_CONTEXT_TOKENS;
        if (contextTokenEnd < 10) contextTokenEnd = 10;

        int bodyStartIdx = contextTokenEnd;

        // We now need space for TWO [SEP] tokens (one in the middle, one at the end)
        int availableSpaceForBody = MAX_TOKENS - contextTokenEnd - 2;
        int STRIDE = (int) (availableSpaceForBody * 0.85);

        int chunksProcessed = 0;

        // 102 is the standard [SEP] token ID for DistilBERT
        long sepToken = 102L;

        for (int start = bodyStartIdx; start < totalTokens - 1; start += STRIDE) {
            if (chunksProcessed >= MAX_CHUNKS_TO_SCAN) break;

            int currentBodyTokens = Math.min(availableSpaceForBody, (totalTokens - 1) - start);

            // Total size: Context + [SEP] + BodyChunk + [SEP]
            int currentTotalChunkSize = contextTokenEnd + 1 + currentBodyTokens + 1;

            long[] chunkInputIds = new long[currentTotalChunkSize];
            long[] chunkAttentionMask = new long[currentTotalChunkSize];

            // Copy Context (Includes the starting [CLS])
            System.arraycopy(allInputIds, 0, chunkInputIds, 0, contextTokenEnd);
            System.arraycopy(allAttentionMask, 0, chunkAttentionMask, 0, contextTokenEnd);

            // Inject the MIDDLE [SEP] token to act as logical glue
            chunkInputIds[contextTokenEnd] = sepToken;
            chunkAttentionMask[contextTokenEnd] = 1;

            // Copy the current Body sliding window
            System.arraycopy(allInputIds, start, chunkInputIds, contextTokenEnd + 1, currentBodyTokens);
            System.arraycopy(allAttentionMask, start, chunkAttentionMask, contextTokenEnd + 1, currentBodyTokens);

            // Append the FINAL [SEP] token
            chunkInputIds[currentTotalChunkSize - 1] = sepToken;
            chunkAttentionMask[currentTotalChunkSize - 1] = 1;

            // Inference
            boolean isAttack = processSingleChunk(chunkInputIds, chunkAttentionMask, chunksProcessed, startTime);
            if (isAttack) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Attack detected in chunk {} (Tokens {}-{})!", chunksProcessed + 1, start, start + currentBodyTokens);
                }
                return true;
            }

            chunksProcessed++;
            if (start + currentBodyTokens >= totalTokens - 1) break;
        }

        if (logger.isDebugEnabled()) {
            long endTime = System.currentTimeMillis();
            logger.debug("WAF Analysis Execution Time ({} chunks): {} ms", chunksProcessed, (endTime - startTime));
        }
        return false;
    }

    /**
     * Isolated method to run a single Tensor through ONNX
     */
    private boolean processSingleChunk(long[] chunkInputIds, long[] chunkAttentionMask, int chunkIndex, long startTime) throws Exception {
        int currentChunkSize = chunkInputIds.length;
        long[] shape = new long[]{1, currentChunkSize};

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

                double expSafe = Math.exp(output[0][0]);
                double expMalicious = Math.exp(output[0][1]);
                double attackProbability = expMalicious / (expSafe + expMalicious);

                if (logger.isDebugEnabled()) {
                    logger.debug("WAF AI Score (Chunk {}) - Safe: {}%, Attack: {}%",
                            chunkIndex + 1,
                            String.format("%.2f", (1.0 - attackProbability) * 100),
                            String.format("%.2f", attackProbability * 100));
                }

                if (attackProbability > THRESHOLD) {
                     if (logger.isDebugEnabled()) {
                         long endTime = System.currentTimeMillis();
                         logger.debug("WAF Attack Found! Total Execution Time: {} ms", (endTime - startTime));
                     }
                     return true;
                }
            }
        }
        if (logger.isDebugEnabled()) {
            long endTime = System.currentTimeMillis();
            logger.debug("WAF AI Safe Request! Total Execution Time: {} ms", (endTime - startTime));
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
