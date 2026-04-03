/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.media.localsearch;

import static com.android.providers.media.localsearch.ODIUtils.createAppSearchEmbeddingVectorFromResponse;
import static com.android.providers.media.localsearch.ODIUtils.createEmbeddingRequestForText;
import static com.android.providers.media.localsearch.ODIUtils.createEmbeddingVectorListFromResponse;
import static com.android.providers.media.localsearch.ODIUtils.createImageDescriptionFromResponse;
import static com.android.providers.media.localsearch.ODIUtils.createImageDescriptionRequestForMedia;

import android.annotation.NonNull;
import android.app.ondeviceintelligence.ModelDownloadCallback;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.embedding.EmbeddingModel;
import android.app.ondeviceintelligence.embedding.EmbeddingRequest;
import android.app.ondeviceintelligence.embedding.EmbeddingResponse;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionCallback;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionModel;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionRequest;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionResponse;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.SystemClock;
import android.provider.mediaprocessingservice.EmbeddingVector;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class for {@link OnDeviceIntelligenceManager} to handle embedding-related tasks
 * synchronously.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
public final class ODIMWrapper {
    private static final String TAG = "ODIMWrapper";

    // TODO(b/492016263) : Fine tune timeout durations
    private static final int LIST_EMBEDDING_MODELS_TIMEOUT_SECONDS = 60;
    private static final int FETCH_EMBEDDING_MODEL_TIMEOUT_SECONDS = 10;
    private static final int GET_MODEL_STATUS_TIMEOUT_SECONDS = 5;
    private static final int GENERATE_EMBEDDINGS_FOR_MEDIA_TIMEOUT_SECONDS = 300;
    private static final int GENERATE_EMBEDDING_FOR_TEXT_TIMEOUT_SECONDS = 30;
    private static final int GENERATE_EMBEDDINGS_REQUEST_LIMIT = 100;
    private static final int FETCH_IMAGE_DESCRIPTION_MODEL_TIMEOUT_SECONDS = 60;
    private static final int GENERATE_DESCRIPTION_FOR_MEDIA_TIMEOUT_SECONDS = 300;
    private static final int GENERATE_DESCRIPTION_REQUEST_LIMIT = 100;
    private final OnDeviceIntelligenceManager mIntelligenceManager;
    private final Executor mExecutor;
    private final Context mContext;

    public ODIMWrapper(@NonNull Context context, @NonNull Executor executor) {
        mContext = context;
        mIntelligenceManager = context.getSystemService(OnDeviceIntelligenceManager.class);
        mExecutor = executor;
    }

    /**
     * Synchronously lists the available embedding models.
     *
     * @return a list of available {@link EmbeddingModel}s.
     */
    @NonNull
    private List<EmbeddingModel> listEmbeddingModels() {
        if (mIntelligenceManager == null) {
            throw new UnsupportedOperationException("OnDeviceIntelligenceManager is not available");
        }

        CompletableFuture<List<EmbeddingModel>> future = new CompletableFuture<>();
        mIntelligenceManager.listEmbeddingModels(mExecutor,
                new OutcomeReceiver<List<EmbeddingModel>, OnDeviceIntelligenceException>() {
                    @Override
                    public void onResult(List<EmbeddingModel> result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(OnDeviceIntelligenceException error) {
                        future.completeExceptionally(error);
                    }
                });

        try {
            List<EmbeddingModel> models = future.get(LIST_EMBEDDING_MODELS_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
            if (models == null || models.isEmpty()) {
                throw new UnsupportedOperationException("Embedding models list is null or empty");
            }
            return models;
        } catch (Exception e) {
            Log.e(TAG, "listEmbeddingModels: Error getting embedding models", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Synchronously fetches a specific embedding model by its signature.
     *
     * @param modelSignature the signature of the model to fetch.
     * @return the requested {@link EmbeddingModel}.
     */
    @NonNull
    private EmbeddingModel fetchEmbeddingModel(String modelSignature) {
        if (modelSignature == null) {
            throw new IllegalArgumentException("Model Signature is null");
        }

        if (mIntelligenceManager == null) {
            throw new UnsupportedOperationException("OnDeviceIntelligenceManager is not available");
        }

        CompletableFuture<EmbeddingModel> future = new CompletableFuture<>();
        mIntelligenceManager.fetchEmbeddingModel(modelSignature, mExecutor,
                new OutcomeReceiver<EmbeddingModel, OnDeviceIntelligenceException>() {
                    @Override
                    public void onResult(EmbeddingModel result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        future.completeExceptionally(error);
                    }
                });

        try {
            EmbeddingModel model = future.get(FETCH_EMBEDDING_MODEL_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
            if (model == null) {
                throw new UnsupportedOperationException("Embedding Model is null");
            }
            return model;
        } catch (Exception e) {
            Log.e(TAG, "fetchEmbeddingModel: Error fetching embedding model", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Synchronously fetches the status of a specific embedding model.
     *
     * @param model the embedding model whose status is to be fetched.
     * @return the status of the model.
     */
    @NonNull
    private Integer getEmbeddingModelStatus(EmbeddingModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Embedding Model is null");
        }

        CompletableFuture<Integer> future = new CompletableFuture<>();
        model.getStatus(mExecutor, new OutcomeReceiver<Integer, OnDeviceIntelligenceException>() {
            @Override
            public void onResult(Integer result) {
                future.complete(result);
            }

            @Override
            public void onError(@NonNull OnDeviceIntelligenceException error) {
                future.completeExceptionally(error);
            }
        });

        try {
            Integer status = future.get(GET_MODEL_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (status == null) {
                throw new UnsupportedOperationException("Embedding Model status is null");
            }
            return status;
        } catch (Exception e) {
            Log.e(TAG, "getEmbeddingModelStatus: Error fetching model status", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Asynchronously downloads a specific embedding model.
     *
     * @param model  the embedding model to be downloaded.
     * @param signal a cancellation signal to stop the download.
     * @return a {@link CompletableFuture} that resolves to {@code true} if the download was
     * successful, {@code false} otherwise.
     */
    private CompletableFuture<Boolean> downloadEmbeddingModel(EmbeddingModel model,
            CancellationSignal signal) {
        if (model == null) {
            throw new IllegalArgumentException("Embedding Model is null");
        }

        long downloadStartTime = SystemClock.elapsedRealtime();

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        model.download(signal, mExecutor, new ModelDownloadCallback() {
            @Override
            public void onDownloadStarted(long bytesToDownload) {
                Log.d(TAG, "Download started for " + model.getModelSignature() + ", Total "
                        + "bytes to download: " + bytesToDownload);
            }

            @Override
            public void onDownloadProgress(long bytesDownloaded) {
                Log.d(TAG, "Download in progress for " + model.getModelSignature()
                        + ", bytes downloaded: " + bytesDownloaded + " Time elapsed: " + (
                        SystemClock.elapsedRealtime() - downloadStartTime) + "ms");
            }

            @Override
            public void onDownloadFailed(int failureStatus, @Nullable String errorMessage) {
                Log.d(TAG, "Download failed for " + model.getModelSignature() + ". Error: "
                        + errorMessage + " Time elapsed: " + (SystemClock.elapsedRealtime()
                        - downloadStartTime) + "ms");
                future.complete(false);
            }

            @Override
            public void onDownloadCompleted() {
                Log.d(TAG, "Download completed for " + model.getModelSignature() + " Time elapsed: "
                        + (SystemClock.elapsedRealtime() - downloadStartTime) + "ms");
                future.complete(true);
            }
        });

        return future;
    }

    /**
     * Synchronously generates embeddings for a list of media files identified by their Uris.
     *
     * @param model               the embedding model to use.
     * @param uriToDescriptionMap a map of media URIs to their corresponding text descriptions.
     * @param signal              a cancellation signal to stop the generation.
     * @return a map of media URIs to their generated list of {@link EmbeddingVector}s.
     * @throws IllegalArgumentException if model is null or the requests batch exceeds size limit.
     * @throws IllegalStateException    if there is an error generating the embeddings.
     */
    @NonNull
    public Map<Uri, List<EmbeddingVector>> generateEmbeddingsForMedia(EmbeddingModel model,
            Map<Uri, List<String>> uriToDescriptionMap, CancellationSignal signal) {
        if (model == null) {
            throw new IllegalArgumentException("Embedding Model is null");
        }

        if (uriToDescriptionMap == null || uriToDescriptionMap.isEmpty()) {
            Log.d(TAG, "No media files provided for embedding generation");
            return new HashMap<>();
        }

        if (uriToDescriptionMap.size() > GENERATE_EMBEDDINGS_REQUEST_LIMIT) {
            throw new IllegalArgumentException("Requests batch exceeded size limit. Limit: "
                    + GENERATE_EMBEDDINGS_REQUEST_LIMIT + " Actual: " + uriToDescriptionMap.size());
        }

        try {
            HashMap<Uri, EmbeddingRequest> uriToEmbeddingRequestMap = new HashMap<>();
            for (Map.Entry<Uri, List<String>> descriptionResponseEntry :
                    uriToDescriptionMap.entrySet()) {
                if (!descriptionResponseEntry.getValue().isEmpty()) {
                    uriToEmbeddingRequestMap.put(descriptionResponseEntry.getKey(),
                            createEmbeddingRequestForText(descriptionResponseEntry.getValue()));
                }
            }

            ConcurrentHashMap<Uri, EmbeddingResponse> uriToEmbeddingResponseMap =
                    new ConcurrentHashMap<>();
            CountDownLatch responseLatch = new CountDownLatch(uriToEmbeddingRequestMap.size());

            for (Map.Entry<Uri, EmbeddingRequest> requestEntry :
                    uriToEmbeddingRequestMap.entrySet()) {
                if (signal.isCanceled()) {
                    Log.d(TAG, "Processing cancelled. Skipping remaining request for uri: "
                            + requestEntry.getKey());
                    responseLatch.countDown();
                    continue;
                }

                Uri uri = requestEntry.getKey();
                EmbeddingRequest request = requestEntry.getValue();
                if (request == null) {
                    responseLatch.countDown();
                    continue;
                }

                try {
                    model.generateEmbeddings(request, signal, mExecutor,
                            new OutcomeReceiver<EmbeddingResponse,
                                    OnDeviceIntelligenceException>() {
                                @Override
                                public void onResult(EmbeddingResponse result) {
                                    uriToEmbeddingResponseMap.put(uri, result);
                                    responseLatch.countDown();
                                }

                                @Override
                                public void onError(@NonNull OnDeviceIntelligenceException error) {
                                    Log.e(TAG, "Error generating embeddings for uri: " + uri,
                                            error);
                                    responseLatch.countDown();
                                }
                            });
                } catch (RuntimeException e) {
                    // Catch synchronous failures (e.g., IPC failures, invalid arguments,
                    // executor rejections)
                    Log.e(TAG, "generateEmbeddingsForMedia: Synchronous failure dispatching "
                            + "embedding request for uri: " + uri, e);
                    responseLatch.countDown();
                }
            }

            if (!responseLatch.await(GENERATE_EMBEDDINGS_FOR_MEDIA_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                Log.w(TAG, "generateEmbeddingsForMedia: Timed out waiting for embeddings. "
                        + "Returning partial results: " + uriToEmbeddingResponseMap.size()
                        + " out of " + uriToEmbeddingRequestMap.size() + " completed.");
            }

            HashMap<Uri, List<EmbeddingVector>> uriToEmbeddingsMap = new HashMap<>();

            for (Map.Entry<Uri, EmbeddingResponse> response :
                    uriToEmbeddingResponseMap.entrySet()) {
                uriToEmbeddingsMap.put(response.getKey(),
                        createEmbeddingVectorListFromResponse(response.getValue(),
                                model.getModelSignature()));
            }


            return uriToEmbeddingsMap;
        } catch (Exception e) {
            Log.e(TAG, "generateEmbeddingsForMedia: Error generating embeddings", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Synchronously generates embeddings for the provided text string.
     *
     * @param model  the embedding model to use.
     * @param text   the text to generate embeddings for.
     * @param signal a cancellation signal to stop the generation.
     * @return a list of generated {@link EmbeddingVector}s, or an empty list if input is empty.
     * @throws IllegalArgumentException if model is null.
     * @throws IllegalStateException    if there is an error generating the embeddings for search
     *                                  text.
     */
    @NonNull
    public List<androidx.appsearch.app.EmbeddingVector> generateEmbeddingForSearchText(
            EmbeddingModel model, String text, CancellationSignal signal) {
        if (model == null) {
            throw new IllegalArgumentException("Embedding Model is null");
        }

        if (text == null || text.isEmpty()) {
            Log.d(TAG, "Empty string provided for embedding generation");
            return new ArrayList<>();
        }

        try {
            EmbeddingRequest request = createEmbeddingRequestForText(List.of(text));
            CompletableFuture<EmbeddingResponse> future = new CompletableFuture<>();

            model.generateEmbeddings(request, signal, mExecutor,
                    new OutcomeReceiver<EmbeddingResponse, OnDeviceIntelligenceException>() {
                        @Override
                        public void onResult(EmbeddingResponse result) {
                            future.complete(result);
                        }

                        @Override
                        public void onError(@NonNull OnDeviceIntelligenceException error) {
                            future.completeExceptionally(error);
                        }
                    });

            EmbeddingResponse response = future.get(GENERATE_EMBEDDING_FOR_TEXT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);

            return createAppSearchEmbeddingVectorFromResponse(response, model.getModelSignature());
        } catch (Exception e) {
            Log.e(TAG, "generateEmbeddingForSearchText: "
                    + "Error generating embeddings for search text", e);
            throw new IllegalStateException(e);
        }
    }


    /**
     * Synchronously fetches the first available image description model.
     *
     * <p> ImageDescription is updated under the hood. There is a single version available at any
     * time per Locale. Currently only English locale is supported.
     *
     * @return the {@link ImageDescriptionModel} instance.
     */
    @NonNull
    private ImageDescriptionModel fetchImageDescriptionModel() {
        if (mIntelligenceManager == null) {
            throw new UnsupportedOperationException("OnDeviceIntelligenceManager is not available");
        }

        CompletableFuture<List<ImageDescriptionModel>> future = new CompletableFuture<>();
        mIntelligenceManager.listImageDescriptionModels(mExecutor,
                new OutcomeReceiver<List<ImageDescriptionModel>, OnDeviceIntelligenceException>() {
                    @Override
                    public void onResult(List<ImageDescriptionModel> result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        future.completeExceptionally(error);
                    }
                });

        try {
            List<ImageDescriptionModel> descriptionModels = future.get(
                    FETCH_IMAGE_DESCRIPTION_MODEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (descriptionModels == null || descriptionModels.isEmpty()) {
                throw new UnsupportedOperationException("No image description models found.");
            }

            ImageDescriptionModel model = descriptionModels.get(0);

            if (model == null) {
                throw new UnsupportedOperationException("No image description model found.");
            }

            return model;
        } catch (Exception e) {
            Log.e(TAG, "fetchImageDescriptionModel: Error getting image description model", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Synchronously fetches the status of a specific image description model.
     *
     * @param model the image description model whose status is to be fetched.
     * @return the status of the model.
     */
    @NonNull
    private Integer getImageDescriptionModelStatus(ImageDescriptionModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Image Description Model is null");
        }

        CompletableFuture<Integer> future = new CompletableFuture<>();
        model.getStatus(mExecutor, new OutcomeReceiver<Integer, OnDeviceIntelligenceException>() {
            @Override
            public void onResult(Integer result) {
                future.complete(result);
            }

            @Override
            public void onError(@NonNull OnDeviceIntelligenceException error) {
                future.completeExceptionally(error);
            }
        });

        try {
            Integer status = future.get(GET_MODEL_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (status == null) {
                throw new UnsupportedOperationException("ImageDescriptionModel status is null");
            }
            return status;
        } catch (Exception e) {
            Log.e(TAG, "getImageDescriptionModelStatus: Error fetching model status", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Asynchronously downloads a specific image description model.
     *
     * @param model  the image description model to be downloaded.
     * @param signal a cancellation signal to stop the download.
     * @return a {@link CompletableFuture} that resolves to {@code true} if the download was
     * successful, {@code false} otherwise.
     */
    private CompletableFuture<Boolean> downloadImageDescriptionModel(ImageDescriptionModel model,
            CancellationSignal signal) {
        if (model == null) {
            throw new IllegalArgumentException("Image Description Model is null");
        }

        long downloadStartTime = SystemClock.elapsedRealtime();

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        model.download(signal, mExecutor, new ModelDownloadCallback() {
            @Override
            public void onDownloadStarted(long bytesToDownload) {
                Log.d(TAG, "Download started for " + model.getModelSignature() + ", Total "
                        + "bytes to download: " + bytesToDownload);
            }

            @Override
            public void onDownloadProgress(long bytesDownloaded) {
                Log.d(TAG, "Download in progress for " + model.getModelSignature()
                        + ", bytes downloaded: " + bytesDownloaded + " Time elapsed: " + (
                        SystemClock.elapsedRealtime() - downloadStartTime) + "ms");
            }

            @Override
            public void onDownloadFailed(int failureStatus, @Nullable String errorMessage) {
                Log.d(TAG, "Download failed for " + model.getModelSignature() + ". Error: "
                        + errorMessage + " Time elapsed: " + (SystemClock.elapsedRealtime()
                        - downloadStartTime) + "ms");
                future.complete(false);
            }

            @Override
            public void onDownloadCompleted() {
                Log.d(TAG, "Download completed for " + model.getModelSignature() + " Time elapsed: "
                        + (SystemClock.elapsedRealtime() - downloadStartTime) + "ms");
                future.complete(true);
            }
        });

        return future;
    }

    /**
     * Synchronously generates image descriptions for a list of media files identified by their
     * URIs.
     *
     * @param model  the image description model to use for generation.
     * @param uris   a list of URIs to generate image descriptions for.
     * @param signal a cancellation signal to stop the generation.
     * @return a map of URIs to their generated list of image descriptions.
     */
    @NonNull
    public Map<Uri, List<String>> generateImageDescriptionForMedia(ImageDescriptionModel model,
            List<Uri> uris, CancellationSignal signal) {
        if (model == null) {
            throw new IllegalArgumentException("Image Description Model is null");
        }

        if (uris == null || uris.isEmpty()) {
            Log.d(TAG, "No media files provided for image description generation");
            return new HashMap<>();
        }

        if (uris.size() > GENERATE_DESCRIPTION_REQUEST_LIMIT) {
            throw new IllegalArgumentException("Requests batch exceeded size limit. Limit: "
                    + GENERATE_DESCRIPTION_REQUEST_LIMIT + " Actual: " + uris.size());
        }

        try {
            HashMap<Uri, ImageDescriptionRequest> imageDescriptionRequests = new HashMap<>();
            for (Uri uri : uris) {
                imageDescriptionRequests.put(uri,
                        createImageDescriptionRequestForMedia(mContext, uri, /* prompt */ null));
            }

            ConcurrentHashMap<Uri, List<String>> uriToDescriptionMap = new ConcurrentHashMap<>();
            CountDownLatch responseLatch = new CountDownLatch(imageDescriptionRequests.size());

            for (Map.Entry<Uri, ImageDescriptionRequest> requestEntry :
                    imageDescriptionRequests.entrySet()) {
                if (signal.isCanceled()) {
                    Log.d(TAG, "Processing cancelled. Skipping remaining request for uri: "
                            + requestEntry.getKey());
                    responseLatch.countDown();
                    continue;
                }

                Uri uri = requestEntry.getKey();
                ImageDescriptionRequest request = requestEntry.getValue();
                if (request == null) {
                    responseLatch.countDown();
                    continue;
                }

                try {
                    model.generateImageDescription(request, signal, mExecutor,
                            new ImageDescriptionCallback() {
                                @Override
                                public void onNewText(@NonNull String text) {
                                    // Do nothing. Wait for complete result.
                                }

                                /**
                                 * Called when the image description generation is complete.
                                 *
                                 * <p> After the streaming is complete, this method will be invoked
                                 * with the cumulative output. If the total output is too large to
                                 * send via binder, the {@link ImageDescriptionResponse}
                                 * may be populated with an empty description to indicate that the
                                 * streaming is complete, as the content has already been
                                 * streamed via {@link #onNewText(String)}.
                                 */
                                @Override
                                public void onResult(@NonNull ImageDescriptionResponse result) {
                                    if (result != null
                                            && !result.getImageDescriptions().isEmpty()) {
                                        uriToDescriptionMap.put(uri,
                                                createImageDescriptionFromResponse(result));
                                    }
                                    responseLatch.countDown();
                                }

                                @Override
                                public void onError(@NonNull OnDeviceIntelligenceException error) {
                                    Log.e(TAG, "Error generating image description for uri: " + uri,
                                            error);
                                    responseLatch.countDown();
                                }
                            });
                } catch (RuntimeException e) {
                    // Catch synchronous failures (e.g., IPC failures, invalid arguments,
                    // executor rejections)
                    Log.e(TAG, "generateImageDescriptionForMedia: Synchronous failure "
                            + "dispatching image description request for uri: " + uri, e);
                    responseLatch.countDown();
                }
            }

            if (!responseLatch.await(GENERATE_DESCRIPTION_FOR_MEDIA_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                Log.w(TAG, "generateImageDescriptionForMedia: "
                        + "Timed out waiting for image descriptions. "
                        + "Returning partial results: " + uriToDescriptionMap.size() + " out of "
                        + imageDescriptionRequests.size() + " completed.");
            }

            return uriToDescriptionMap;
        } catch (Exception e) {
            Log.e(TAG, "generateImageDescriptionForMedia: Error generating image descriptions", e);
            throw new IllegalStateException(e);
        }
    }
}
