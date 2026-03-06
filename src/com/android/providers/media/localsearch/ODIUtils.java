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

import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.app.ondeviceintelligence.Content;
import android.app.ondeviceintelligence.Part;
import android.app.ondeviceintelligence.embedding.EmbeddingRequest;
import android.app.ondeviceintelligence.embedding.EmbeddingResponse;
import android.os.Build;

import androidx.appsearch.app.EmbeddingVector;

import java.util.ArrayList;
import java.util.List;

public final class ODIUtils {
    private static final String TAG = "ODIUtils";

    /**
     * Creates an ondeviceintelligence supported {@link EmbeddingRequest} object for the provided
     * list of text strings.
     *
     * @param textList the list of text strings to generate embeddings for.
     * @return an {@link EmbeddingRequest} object.
     * @throws IllegalArgumentException if textList is null or empty.
     */
    @NonNull
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static EmbeddingRequest createEmbeddingRequestForText(@NonNull List<String> textList) {
        if (textList == null || textList.isEmpty()) {
            throw new IllegalArgumentException("String argument should be non-null");
        }

        List<Part> parts = new ArrayList<>();
        for (String text : textList) {
            parts.add(Part.createText(text));
        }
        Content content = new Content(parts);
        return new EmbeddingRequest(List.of(content));
    }

    /**
     * Creates an AppSearch compatible {@link EmbeddingVector} list from the
     * provided {@link EmbeddingResponse} and the embedding model signature
     */
    @NonNull
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static List<EmbeddingVector> createAppSearchEmbeddingVectorFromResponse(
            @NonNull EmbeddingResponse response, @NonNull String modelSignature) {
        if (response == null || modelSignature == null) {
            throw new IllegalArgumentException(
                    "Argument should be non-null. EmbeddingResponse: " + response
                            + " Model Signature: " + modelSignature);
        }

        if (response.getEmbeddings().isEmpty()) {
            return new ArrayList<>();
        }

        List<android.app.ondeviceintelligence.embedding.EmbeddingVector> embeddingVectors =
                response.getEmbeddings();
        List<EmbeddingVector> resultVectors = new ArrayList<>();

        for (android.app.ondeviceintelligence.embedding.EmbeddingVector embeddingVector :
                embeddingVectors) {
            resultVectors.add(new EmbeddingVector(embeddingVector.getVector(), modelSignature));
        }

        return resultVectors;
    }

    /**
     * Creates a list of {@link android.provider.mediaprocessingservice.EmbeddingVector}s from the
     * provided {@link EmbeddingResponse} and the embedding model signature.
     *
     * @param response       the {@link EmbeddingResponse} from the on-device intelligence service.
     * @param modelSignature the signature of the model used to generate the embeddings.
     * @return a list of {@link android.provider.mediaprocessingservice.EmbeddingVector}s.
     * @throws IllegalArgumentException if the provided response or modelSignature is null.
     */
    @NonNull
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static List<android.provider.mediaprocessingservice.EmbeddingVector>
            createEmbeddingVectorListFromResponse(
                    @NonNull EmbeddingResponse response, String modelSignature) {
        if (response == null || modelSignature == null) {
            throw new IllegalArgumentException(
                    "Argument should be non-null. EmbeddingResponse: " + response
                            + " Model Signature: " + modelSignature);
        }

        if (response.getEmbeddings().isEmpty()) {
            return new ArrayList<>();
        }

        List<android.app.ondeviceintelligence.embedding.EmbeddingVector> embeddingVectors =
                response.getEmbeddings();
        List<android.provider.mediaprocessingservice.EmbeddingVector> resultVectors =
                new ArrayList<>();

        for (android.app.ondeviceintelligence.embedding.EmbeddingVector embeddingVector :
                embeddingVectors) {
            resultVectors.add(new android.provider.mediaprocessingservice.EmbeddingVector(
                    embeddingVector.getVector(), modelSignature));
        }

        return resultVectors;
    }
}
