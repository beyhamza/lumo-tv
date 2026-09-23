package org.openapitools.configuration;

import tv.lumo.api.generated.model.ContentType;
import tv.lumo.api.generated.model.EntitlementProvider;
import tv.lumo.api.generated.model.EntitlementStatus;
import tv.lumo.api.generated.model.EpgAttemptStatus;
import tv.lumo.api.generated.model.EpgMappingStatus;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.Locale;
import tv.lumo.api.generated.model.Plan;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.ProgressItemType;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SourceStatus;
import tv.lumo.api.generated.model.SyncStep;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration(value = "org.openapitools.configuration.enumConverterConfiguration")
public class EnumConverterConfiguration {

    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.contentTypeConverter")
    Converter<String, ContentType> contentTypeConverter() {
        return new Converter<String, ContentType>() {
            @Override
            public ContentType convert(String source) {
                return ContentType.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.entitlementProviderConverter")
    Converter<String, EntitlementProvider> entitlementProviderConverter() {
        return new Converter<String, EntitlementProvider>() {
            @Override
            public EntitlementProvider convert(String source) {
                return EntitlementProvider.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.entitlementStatusConverter")
    Converter<String, EntitlementStatus> entitlementStatusConverter() {
        return new Converter<String, EntitlementStatus>() {
            @Override
            public EntitlementStatus convert(String source) {
                return EntitlementStatus.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.epgAttemptStatusConverter")
    Converter<String, EpgAttemptStatus> epgAttemptStatusConverter() {
        return new Converter<String, EpgAttemptStatus>() {
            @Override
            public EpgAttemptStatus convert(String source) {
                return EpgAttemptStatus.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.epgMappingStatusConverter")
    Converter<String, EpgMappingStatus> epgMappingStatusConverter() {
        return new Converter<String, EpgMappingStatus>() {
            @Override
            public EpgMappingStatus convert(String source) {
                return EpgMappingStatus.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.errorCodeConverter")
    Converter<String, ErrorCode> errorCodeConverter() {
        return new Converter<String, ErrorCode>() {
            @Override
            public ErrorCode convert(String source) {
                return ErrorCode.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.ingestionErrorCodeConverter")
    Converter<String, IngestionErrorCode> ingestionErrorCodeConverter() {
        return new Converter<String, IngestionErrorCode>() {
            @Override
            public IngestionErrorCode convert(String source) {
                return IngestionErrorCode.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.localeConverter")
    Converter<String, Locale> localeConverter() {
        return new Converter<String, Locale>() {
            @Override
            public Locale convert(String source) {
                return Locale.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.planConverter")
    Converter<String, Plan> planConverter() {
        return new Converter<String, Plan>() {
            @Override
            public Plan convert(String source) {
                return Plan.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.platformConverter")
    Converter<String, Platform> platformConverter() {
        return new Converter<String, Platform>() {
            @Override
            public Platform convert(String source) {
                return Platform.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.progressItemTypeConverter")
    Converter<String, ProgressItemType> progressItemTypeConverter() {
        return new Converter<String, ProgressItemType>() {
            @Override
            public ProgressItemType convert(String source) {
                return ProgressItemType.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.sourceKindConverter")
    Converter<String, SourceKind> sourceKindConverter() {
        return new Converter<String, SourceKind>() {
            @Override
            public SourceKind convert(String source) {
                return SourceKind.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.sourceStatusConverter")
    Converter<String, SourceStatus> sourceStatusConverter() {
        return new Converter<String, SourceStatus>() {
            @Override
            public SourceStatus convert(String source) {
                return SourceStatus.fromValue(source);
            }
        };
    }
    @Bean(name = "org.openapitools.configuration.EnumConverterConfiguration.syncStepConverter")
    Converter<String, SyncStep> syncStepConverter() {
        return new Converter<String, SyncStep>() {
            @Override
            public SyncStep convert(String source) {
                return SyncStep.fromValue(source);
            }
        };
    }

}
