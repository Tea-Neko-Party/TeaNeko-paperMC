package org.zexnocs.teaneko.core.file_config.parser;

import org.zexnocs.teaneko.core.file_config.api.FileConfigType;
import org.zexnocs.teaneko.core.file_config.api.IFileConfigData;
import org.zexnocs.teaneko.core.file_config.interfaces.FileTypeParser;
import org.zexnocs.teaneko.core.file_config.interfaces.IFileTypeParser;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 用于解析 {@link FileConfigType#JSON} 的解析器。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 */
@FileTypeParser(FileConfigType.JSON)
public class JsonFileTypeParser implements IFileTypeParser {
    /// object mapper，支持注释。
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
            .findAndAddModules()
            .build();

    /**
     * 根据输入流解析成相应的 {@link IFileConfigData}
     * <br>该类不负责关闭 {@link InputStream}，请在调用类里关闭。
     *
     * @param stream 输入流
     * @param clazz  要解析的类
     * @return {@link T }
     */
    @Override
    public <T extends IFileConfigData> T fromFileToData(InputStream stream, Class<T> clazz) {
        return objectMapper.readValue(stream, clazz);
    }

    /**
     * 根据 IFileConfigData 来写入到文件中。
     *
     * @param path 路径
     * @param data 数据
     */
    @Override
    public void fromDataToWrite(Path path, IFileConfigData data) {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), data);
    }

    /**
     * 获取该数据的后缀名，不包括 "."。
     * <br>例如 JSON 则返回 "json"
     *
     * @return 后缀名
     */
    @Override
    public String getSuffix() {
        return "json";
    }
}
