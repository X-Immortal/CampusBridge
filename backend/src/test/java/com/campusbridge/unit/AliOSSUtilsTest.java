package com.campusbridge.unit;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.campusbridge.utils.AliOSSUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AliOSSUtilsTest {
    @Test
    void failedUploadStillShutsDownOssClient() throws Exception {
        OSS client = mock(OSS.class);
        OSSClientBuilder.OSSClientBuilderImpl builder =
                mock(OSSClientBuilder.OSSClientBuilderImpl.class, RETURNS_SELF);
        EnvironmentVariableCredentialsProvider credentials = mock(EnvironmentVariableCredentialsProvider.class);
        when(builder.build()).thenReturn(client);
        when(client.putObject(anyString(), anyString(), any(InputStream.class)))
                .thenThrow(new IllegalStateException("upload failed"));
        AliOSSUtils uploader = new AliOSSUtils();
        ReflectionTestUtils.setField(uploader, "endpoint", "https://oss.example.com");
        ReflectionTestUtils.setField(uploader, "bucketName", "test");
        ReflectionTestUtils.setField(uploader, "region", "test-region");
        try (MockedStatic<OSSClientBuilder> builders = mockStatic(OSSClientBuilder.class);
             MockedStatic<CredentialsProviderFactory> providers = mockStatic(CredentialsProviderFactory.class)) {
            builders.when(OSSClientBuilder::create).thenReturn(builder);
            providers.when(CredentialsProviderFactory::newEnvironmentVariableCredentialsProvider)
                    .thenReturn(credentials);
            MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1});
            assertThatThrownBy(() -> uploader.upload(file))
                    .isInstanceOf(IllegalStateException.class).hasMessage("upload failed");
            verify(client).shutdown();
        }
    }
}
