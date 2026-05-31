package com.aicodehub.mapper;

import com.aicodehub.entity.DocumentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("SELECT * FROM document_chunk WHERE document_id = #{docId} AND MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) LIMIT #{topK}")
    List<DocumentChunk> searchByFulltext(Long docId, String query, int topK);

    @Select("SELECT * FROM document_chunk WHERE document_id = #{docId} AND content LIKE CONCAT('%',#{query},'%') LIMIT #{topK}")
    List<DocumentChunk> searchByLike(Long docId, String query, int topK);
}
