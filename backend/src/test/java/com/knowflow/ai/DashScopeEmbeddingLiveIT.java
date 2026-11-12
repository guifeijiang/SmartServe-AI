package com.knowflow.ai;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.MetadataMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实调用 DashScope text-embedding-v3 的联调用例（会按 token 计费）。
 * 默认不随 mvn test 执行，需显式指定：
 *   mvn -Dtest=DashScopeEmbeddingLiveIT test
 * 需要环境变量 DASHSCOPE_API_KEY。
 */
class DashScopeEmbeddingLiveIT {

    @Test
    void embedsTextWithDashScopeTextEmbeddingV3() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "未配置 DASHSCOPE_API_KEY，跳过真实调用");

        DashScopeApi api = DashScopeApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .apiKey(apiKey)
                .build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel("text-embedding-v3")
                .withDimensions(1024)
                .build();
        DashScopeEmbeddingModel model = DashScopeEmbeddingModel.builder()
                .dashScopeApi(api)
                .metadataMode(MetadataMode.NONE)
                .defaultOptions(options)
                .build();

        float[] vector = model.embed("星港 X9 智能咖啡机退款需要提供哪些材料？");
        assertThat(vector).hasSize(1024);
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        assertThat(norm).isGreaterThan(0);
        System.out.println("[LIVE] text-embedding-v3 dimensions=" + vector.length
                + " norm=" + Math.sqrt(norm)
                + " first3=" + vector[0] + "," + vector[1] + "," + vector[2]);
    }
}
