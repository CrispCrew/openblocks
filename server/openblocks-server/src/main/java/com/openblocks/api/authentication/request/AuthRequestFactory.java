package com.openblocks.api.authentication.request;

import java.util.Set;

import com.openblocks.sdk.auth.AbstractAuthConfig.AuthType;

import reactor.core.publisher.Mono;

public interface AuthRequestFactory {

    Mono<AuthRequest> build(AuthRequestContext context);

    Set<AuthType> supportedAuthTypes();
}