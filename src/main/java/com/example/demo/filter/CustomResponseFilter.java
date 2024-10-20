package com.example.demo.filter;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.stereotype.Component;
import pb.DerivativeOuterClass;
import reactor.core.publisher.Mono;

@Component
public class CustomResponseFilter extends AbstractGatewayFilterFactory<CustomResponseFilter.Config> {

    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;

    public static class Config {
        public Config() {}
    }

    public CustomResponseFilter(ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory) {
        super(Config.class);
        this.modifyResponseBodyFilterFactory = modifyResponseBodyFilterFactory;
    }

    @Override
    public GatewayFilter apply(Config config) {
        final ModifyResponseBodyGatewayFilterFactory.Config modifyResponseBodyFilterFactoryConfig = new ModifyResponseBodyGatewayFilterFactory.Config();
        modifyResponseBodyFilterFactoryConfig.setRewriteFunction(String.class, String.class, (exchange, bodyAsString) -> {
            var byteDataProtobuf = Base64.getDecoder().decode(bodyAsString.getBytes(StandardCharsets.UTF_8));
            try {
                var jsonProtobuf = JsonFormat.printer().print(DerivativeOuterClass.Derivatives.parseFrom(byteDataProtobuf));
                return Mono.just(jsonProtobuf);
            } catch (InvalidProtocolBufferException e) {
                return Mono.empty();
            }
        });
        return modifyResponseBodyFilterFactory.apply(modifyResponseBodyFilterFactoryConfig);
    }
}