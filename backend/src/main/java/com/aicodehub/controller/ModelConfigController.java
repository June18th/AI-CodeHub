package com.aicodehub.controller;

import com.aicodehub.common.Result;
import com.aicodehub.common.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import com.aicodehub.entity.ModelConfig;
import com.aicodehub.mapper.ModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','TEST','ADMIN')")
public class ModelConfigController {

    private final ModelConfigMapper mapper;

    @GetMapping
    @Cacheable(value = "model_configs", key = "T(com.aicodehub.common.UserContext).getUserId()")
    public Result<?> list() {
        return Result.ok(mapper.selectList(new LambdaQueryWrapper<ModelConfig>()
            .eq(ModelConfig::getUserId, UserContext.getUserId())
            .orderByDesc(ModelConfig::getUpdatedAt)));
    }

    @PostMapping
    @CacheEvict(value = "model_configs", key = "T(com.aicodehub.common.UserContext).getUserId()")
    public Result<?> create(@RequestBody ModelConfig config) {
        config.setUserId(UserContext.getUserId());
        mapper.insert(config);
        return Result.ok(Map.of("id", config.getId()));
    }

    @PutMapping("/{id}")
    @CacheEvict(value = "model_configs", key = "T(com.aicodehub.common.UserContext).getUserId()")
    public Result<?> update(@PathVariable Long id, @RequestBody ModelConfig config) {
        ModelConfig exist = mapper.selectById(id);
        if (exist == null) return Result.fail("配置不存在");
        config.setId(id); config.setUserId(UserContext.getUserId());
        mapper.updateById(config);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "model_configs", key = "T(com.aicodehub.common.UserContext).getUserId()")
    public Result<?> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }

    @PutMapping("/{id}/default")
    @CacheEvict(value = "model_configs", key = "T(com.aicodehub.common.UserContext).getUserId()")
    public Result<?> setDefault(@PathVariable Long id) {
        ModelConfig config = mapper.selectById(id);
        if (config == null) return Result.fail("配置不存在");
        LambdaUpdateWrapper<ModelConfig> uw = new LambdaUpdateWrapper<>();
        uw.eq(ModelConfig::getUserId, UserContext.getUserId())
          .eq(ModelConfig::getProvider, config.getProvider())
          .set(ModelConfig::getIsDefault, 0);
        mapper.update(null, uw);
        config.setIsDefault(1);
        mapper.updateById(config);
        return Result.ok();
    }
}
