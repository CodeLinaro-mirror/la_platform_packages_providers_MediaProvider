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

import static com.google.common.truth.Truth.assertThat;

import android.app.ondeviceintelligence.Content;
import android.app.ondeviceintelligence.Part;
import android.app.ondeviceintelligence.embedding.EmbeddingRequest;
import android.app.ondeviceintelligence.embedding.EmbeddingResponse;
import android.app.ondeviceintelligence.embedding.EmbeddingVector;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.CINNAMON_BUN)
public class ODIUtilsTest {
    @Test
    public void testCreateEmbeddingRequestForText() {
        String testText = "search query";
        EmbeddingRequest request = ODIUtils.createEmbeddingRequestForText(List.of(testText));

        assertThat(request.getContent()).hasSize(1);
        Content content = request.getContent().get(0);
        assertThat(content.getParts()).hasSize(1);
        Part part = content.getParts().get(0);
        assertThat(part.getType()).isEqualTo(Part.TYPE_TEXT);
        assertThat(part.getText()).isEqualTo(testText);
    }

    @Test
    public void testCreateAppSearchEmbeddingVectorFromResponse() {
        float[] vectorValues = {0.1f, 0.2f, 0.3f};
        int[] shape = {3};
        String modelSignature = "test_model";
        EmbeddingVector embeddingVector = new EmbeddingVector(vectorValues, shape);
        EmbeddingResponse response = new EmbeddingResponse(List.of(embeddingVector));

        List<androidx.appsearch.app.EmbeddingVector> results =
                ODIUtils.createAppSearchEmbeddingVectorFromResponse(response, modelSignature);

        assertThat(results).hasSize(1);
        androidx.appsearch.app.EmbeddingVector resultVector = results.get(0);
        assertThat(resultVector.getValues()).isEqualTo(vectorValues);
        assertThat(resultVector.getModelSignature()).isEqualTo(modelSignature);
    }

    @Test
    public void testCreateEmbeddingVectorListFromResponse() {
        float[] vectorValues = {0.4f, 0.5f, 0.6f};
        int[] shape = {3};
        String modelSignature = "test_model_mps";
        EmbeddingVector embeddingVector = new EmbeddingVector(vectorValues, shape);
        EmbeddingResponse response = new EmbeddingResponse(List.of(embeddingVector));

        List<android.provider.mediaprocessingservice.EmbeddingVector> results =
                ODIUtils.createEmbeddingVectorListFromResponse(response, modelSignature);

        assertThat(results).hasSize(1);
        android.provider.mediaprocessingservice.EmbeddingVector resultVector = results.get(0);
        assertThat(resultVector.getValues()).isEqualTo(vectorValues);
        assertThat(resultVector.getModelSignature()).isEqualTo(modelSignature);
    }
}
