package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.lj.train.api.admin.AdminModels.CreateOrgCommand;
import me.lj.train.api.admin.AdminModels.OrgView;
import me.lj.train.api.admin.AdminModels.UpdateOrgCommand;
import me.lj.train.api.admin.OrgService;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 根组织部门树REST接口。
 */
@RestController
@RequestMapping("/api/admin/orgs")
public class OrgController {

    @DubboReference(check = false, timeout = 5000, retries = 0)
    private OrgService orgService;

    @GetMapping("/tree")
    @RequirePermission("admin:org:view")
    public Result<List<OrgView>> tree() {
        return Result.ok(RpcResultSupport.unwrap(orgService.tree()));
    }

    @PostMapping
    @RequirePermission("admin:org:create")
    public Result<OrgView> create(@Valid @RequestBody OrgRequest request) {
        return Result.ok(RpcResultSupport.unwrap(orgService.create(
                new CreateOrgCommand(request.parentId(), request.name(), request.code(), request.sortOrder()))));
    }

    @PutMapping("/{id}")
    @RequirePermission("admin:org:update")
    public Result<OrgView> update(@PathVariable Long id, @Valid @RequestBody OrgRequest request) {
        return Result.ok(RpcResultSupport.unwrap(orgService.update(
                new UpdateOrgCommand(id, request.parentId(), request.name(), request.code(), request.sortOrder()))));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:org:delete")
    public Result<?> delete(@PathVariable Long id) {
        RpcResultSupport.ensureSuccess(orgService.delete(id));
        return Result.ok();
    }

    public record OrgRequest(
            Long parentId,
            @NotBlank(message = "部门名称不能为空") String name,
            @NotBlank(message = "部门编码不能为空") String code,
            int sortOrder) {
    }
}
