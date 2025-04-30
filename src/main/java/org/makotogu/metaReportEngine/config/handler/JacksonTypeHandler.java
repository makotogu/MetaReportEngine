package org.makotogu.metaReportEngine.config.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JacksonTypeHandler<T> extends BaseTypeHandler<T> {

    private final TypeReference<T> typeReference;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造器：针对普通 Java 类
     */
    public JacksonTypeHandler(Class<T> type) {
        this.typeReference = new TypeReference<T>() {
            @Override
            public java.lang.reflect.Type getType() {
                return type;
            }
        };
    }

    /**
     * 构造器：针对泛型类型（如 List<T>, Map<K,V>）
     */
    public JacksonTypeHandler(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        try {
            if (parameter instanceof JsonNode) {
                ps.setString(i, parameter.toString());
            } else {
                ps.setString(i, objectMapper.writeValueAsString(parameter));
            }
        } catch (JsonProcessingException e) {
            throw new SQLException("Error converting object to JSON", e);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return json == null ? null : readValue(json);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return json == null ? null : readValue(json);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return json == null ? null : readValue(json);
    }

    private T readValue(String json) throws SQLException {
        try {
            if (typeReference.getType() == JsonNode.class) {
                return (T) objectMapper.readTree(json);
            } else if (typeReference.getType() instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) typeReference.getType();
                if (pt.getRawType() == List.class && pt.getActualTypeArguments()[0] == String.class) {
                    return (T) objectMapper.readValue(json, new TypeReference<List<String>>() {
                    });
                }
            }
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            throw new SQLException("Error parsing JSON: " + json, e);
        }
    }
}
