package com.mojang.authlib.yggdrasil;

import com.mojang.authlib.Environment;
import com.mojang.authlib.EnvironmentParser;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.minecraft.report.AbuseReportLimits;
import com.mojang.authlib.yggdrasil.request.AbuseReportRequest;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

public class YggdrasilAuthenticationService extends HttpAuthenticationService {
    private final Environment environment;
    private final ServicesKeySet servicesKeySet;

    private static final UserApiService.UserProperties DOMREI_OFFLINE_PROPERTIES =
        new UserApiService.UserProperties(
            Set.of(UserApiService.UserFlag.CHAT_ALLOWED, UserApiService.UserFlag.SERVERS_ALLOWED),
            Map.of()
        );

    private static final UserApiService DOMREI_OFFLINE_SERVICE = new UserApiService() {
        @Override
        public UserProperties fetchProperties() throws AuthenticationException {
            return DOMREI_OFFLINE_PROPERTIES;
        }

        @Override
        public boolean isBlockedPlayer(UUID playerID) {
            return false;
        }

        @Override
        public void refreshBlockList() {
        }

        @Override
        public TelemetrySession newTelemetrySession(Executor executor) {
            return TelemetrySession.DISABLED;
        }

        @Override
        public KeyPairResponse getKeyPair() {
            return null;
        }

        @Override
        public void reportAbuse(AbuseReportRequest request) {
        }

        @Override
        public boolean canSendReports() {
            return false;
        }

        @Override
        public AbuseReportLimits getAbuseReportLimits() {
            return AbuseReportLimits.DEFAULTS;
        }
    };

    public YggdrasilAuthenticationService(Proxy proxy) {
        this(proxy, null, determineEnvironment());
    }

    public YggdrasilAuthenticationService(Proxy proxy, Environment environment) {
        this(proxy, null, environment);
    }

    private YggdrasilAuthenticationService(Proxy proxy, ServicesKeySet servicesKeySet, Environment environment) {
        super(proxy);
        this.environment = environment;
        if (servicesKeySet != null) {
            this.servicesKeySet = servicesKeySet;
        } else {
            MinecraftClient client = MinecraftClient.unauthenticated(proxy);
            URL publicKeySetUrl = HttpAuthenticationService.constantURL(environment.servicesHost() + "/publickeys");
            this.servicesKeySet = YggdrasilServicesKeyInfo.get(publicKeySetUrl, client);
        }
    }

    public static YggdrasilAuthenticationService createOffline(Proxy proxy) {
        return new YggdrasilAuthenticationService(proxy, ServicesKeySet.EMPTY, determineEnvironment());
    }

    public static YggdrasilAuthenticationService createOffline(Proxy proxy, Environment environment) {
        return new YggdrasilAuthenticationService(proxy, ServicesKeySet.EMPTY, environment);
    }

    private static Environment determineEnvironment() {
        return EnvironmentParser.getEnvironmentFromProperties()
            .orElse(YggdrasilEnvironment.PROD.getEnvironment());
    }

    public MinecraftSessionService createMinecraftSessionService() {
        return new YggdrasilMinecraftSessionService(this.servicesKeySet, getProxy(), this.environment);
    }

    public GameProfileRepository createProfileRepository() {
        return new YggdrasilGameProfileRepository(getProxy(), this.environment);
    }

    public UserApiService createUserApiService(String accessToken) {
        if (accessToken == null || accessToken.isEmpty() || "0".equals(accessToken) || "null".equals(accessToken)) {
            return DOMREI_OFFLINE_SERVICE;
        }
        return new YggdrasilUserApiService(accessToken, getProxy(), this.environment);
    }

    public ServicesKeySet getServicesKeySet() {
        return this.servicesKeySet;
    }
}
