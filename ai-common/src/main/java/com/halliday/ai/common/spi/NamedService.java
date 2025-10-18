package com.halliday.ai.common.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;

/**
 * 标识型服务的统一约束。
 * <p>
 * 对接第三方服务时，为每个实现提供稳定的 {@link #id()}，便于在客户端或编排层进行选择/切换。
 * 同时允许提供一个友好的展示名称。
 */
public interface NamedService {

    Logger log = LoggerFactory.getLogger(NamedService.class);

    /**
     * 唯一标识符，要求小写且不含空白字符。
     *
     * @return provider id，例如 {@code "sherpa"}、{@code "azure"}
     */
    String id();

    /**
     * 可读性更强的名称。默认回退到 {@link #id()} 的国际化展示。
     *
     * @return display name
     */
    default String displayName() {
        String idValue = Objects.requireNonNull(id(), "id() must not return null");
        log.debug("【命名服务】生成展示名称，原始标识：{}", idValue);
        String display = idValue.isBlank() ? idValue : idValue.substring(0, 1).toUpperCase(Locale.ROOT) + idValue.substring(1);
        log.debug("【命名服务】展示名称生成完成：{}", display);
        return display;
    }
}

