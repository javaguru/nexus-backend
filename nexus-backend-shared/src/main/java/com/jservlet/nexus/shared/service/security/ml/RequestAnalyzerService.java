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
 * Analyzer Request Service with Sequential Sliding Window and ONNX Tensor Padding.
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

    @Value("${nexus.api.backend.analyzer.onnx.path.model:classpath:model/model.onnx}")
    private String pathModel;

    @Value("${nexus.api.backend.analyzer.onnx.path.tokenizer:classpath:model/tokenizer.json}")
    private String pathTokenizer;

    // 4-6 Cpu max recommended
    @Value("${nexus.api.backend.analyzer.onnx.cpu:4}")
    private int CPU;

    // Strict limits to prevent DoS via massive payloads
    @Value("${nexus.api.backend.analyzer.onnx.max-chunks-to-scan:15}")
    private int MAX_CHUNKS_TO_SCAN;

    // Calibrated threshold
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
     * Analyze content text with a pure Sequential Sliding Window.
     * Exact tensor sizing (No forced 512 padding) and sequential execution.
     */
    public boolean isMalicious(String requestPayload) throws Exception {
        long startTime = System.currentTimeMillis();

        // Transform entire text to Tokens natively
        Encoding encoding = tokenizer.encode(requestPayload);
        long[] allInputIds = encoding.getIds();
        long[] allAttentionMask = encoding.getAttentionMask();

        int totalTokens = allInputIds.length;
        int MAX_TOKENS = Integer.parseInt(maxLength); // Mandatory 512!
        int STRIDE = 450; // Stride size to create overlap (0-512, 450-962, 900-1412, etc...)

        int chunksProcessed = 0; // Counter Chunks!
        int numChunks = (int) Math.ceil((double) totalTokens / STRIDE); // Count all Stride Chunks!

        // Pure Chunking with Stride (Overlapping windows over raw tokens)
        for (int start = 0; start < totalTokens; start += STRIDE) {

            if (chunksProcessed >= MAX_CHUNKS_TO_SCAN) {
                logger.warn("WAF AI reached maximum chunks limit ({}). Scan aborted early.", MAX_CHUNKS_TO_SCAN);
                break;
            }

            // Calculate the end of the current chunk (without exceeding total size)
            int end = Math.min(start + MAX_TOKENS, totalTokens);
            int currentChunkSize = end - start;

            // We only create the space that is strictly necessary (no forced padding of 512!)
            long[] chunkInputIds = new long[currentChunkSize];
            long[] chunkAttentionMask = new long[currentChunkSize];

            // Extract subarrays for this chunk
            System.arraycopy(allInputIds, start, chunkInputIds, 0, currentChunkSize);
            System.arraycopy(allAttentionMask, start, chunkAttentionMask, 0, currentChunkSize);

            // Dynamic Shape: [1, 89] ou [1, 512], The CPU calculates the minimum!
            long[] shape = new long[]{1, currentChunkSize};

            try (
                    OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(chunkInputIds), shape);
                    OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(chunkAttentionMask), shape)
            ) {
                Map<String, OnnxTensor> inputs = Map.of(
                        "input_ids", inputIdsTensor,
                        "attention_mask", attentionMaskTensor
                );

                // Execute the model for this specific chunk
                try (OrtSession.Result results = session.run(inputs)) {

                    float[][] output = (float[][]) results.get(0).getValue();

                    // Applying the SOFTMAX function (Conversion to Percentages)
                    double expSafe = Math.exp(output[0][0]); // Raw Log Retrieval
                    double expMalicious = Math.exp(output[0][1]);
                    double attackProbability = expMalicious / (expSafe + expMalicious);

                    // Optional: Log to track the analysis of each chunk
                    if (logger.isDebugEnabled()) {
                        logger.debug("WAF AI Score (Chunk {}/{} - Tokens {}-{}) - Safe: {}%, Attack: {}%",
                                chunksProcessed + 1, numChunks, start, end,
                                String.format("%.2f", (1.0 - attackProbability) * 100),
                                String.format("%.2f", attackProbability * 100));
                    }

                    // Checking the calibrated threshold
                    // 0.50 = Very strict WAF (blocks at the slightest doubt)
                    // 0.65 = Balanced WAF (tolerates noise, blocks real attacks)
                    // 0.85 = Permissive WAF (only blocks absolutely obvious threats)
                    if (attackProbability > THRESHOLD) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Attack detected in chunk {} (Tokens {}-{})!", chunksProcessed + 1, start, end);
                        }
                        if (logger.isDebugEnabled()) {
                            long endTime = System.currentTimeMillis();
                            logger.debug("WAF Analysis Execution Time ({} chunks): {} ms", chunksProcessed, (endTime - startTime));
                        }
                        return true;
                    }
                }
            }

            chunksProcessed++;

            // Optimization: if this chunk reached the absolute end of the payload,
            // no need to compute further overlapping chunks that would just be subsets.
            if (end == totalTokens) {
                break;
            }
        }

        if (logger.isDebugEnabled()) {
            long endTime = System.currentTimeMillis();
            logger.debug("WAF Analysis Execution Time ({} chunks): {} ms", chunksProcessed, (endTime - startTime));
        }
        // If all chunks have been analyzed and none exceeded the threshold, the request is safe.
        return false;
    }

    @PreDestroy
    public void close() throws Exception {
        if (session != null) session.close();
        if (env != null) env.close();
        if (tokenizer != null) tokenizer.close();
    }
}
