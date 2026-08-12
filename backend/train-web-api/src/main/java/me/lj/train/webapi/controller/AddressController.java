package me.lj.train.webapi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import me.lj.train.api.admin.AddressModels.AddressView;
import me.lj.train.api.admin.AddressModels.CreateAddressCommand;
import me.lj.train.api.admin.AddressModels.UpdateAddressCommand;
import me.lj.train.api.admin.AddressService;
import me.lj.train.common.core.result.Result;
import me.lj.train.webapi.security.RequirePermission;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 省、市、区三级行政地址REST接口。
 */
@RestController
@RequestMapping("/api/admin/addresses")
public class AddressController {

    @DubboReference(check = false, timeout = 5000, retries = 0)
    private AddressService addressService;

    /**
     * 按父级行政代码查询直属下级。
     */
    @GetMapping("/children")
    @RequirePermission("admin:address:view")
    public Result<List<AddressView>> children(
            @RequestParam(defaultValue = "0") String parentCode) {
        return Result.ok(RpcResultSupport.unwrap(addressService.children(parentCode)));
    }

    /**
     * 新增行政地址。
     */
    @PostMapping
    @RequirePermission("admin:address:create")
    public Result<AddressView> create(@Valid @RequestBody AddressRequest request) {
        CreateAddressCommand command = new CreateAddressCommand(
                request.getParentCode(), request.getAreaCode(), request.getZipCode(), request.getCityCode(),
                request.getName(), request.getShortName(), request.getMergerName(), request.getPinyin(),
                request.getLng(), request.getLat());
        return Result.ok(RpcResultSupport.unwrap(addressService.create(command)));
    }

    /**
     * 编辑行政地址。
     */
    @PutMapping("/{id}")
    @RequirePermission("admin:address:update")
    public Result<AddressView> update(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        UpdateAddressCommand command = new UpdateAddressCommand(
                id, request.getAreaCode(), request.getZipCode(), request.getCityCode(), request.getName(),
                request.getShortName(), request.getMergerName(), request.getPinyin(),
                request.getLng(), request.getLat());
        return Result.ok(RpcResultSupport.unwrap(addressService.update(command)));
    }

    /**
     * 地址新增及编辑请求。
     */
    public static final class AddressRequest {

        private String parentCode;
        @NotBlank(message = "行政代码不能为空")
        private String areaCode;
        private String zipCode;
        private String cityCode;
        @NotBlank(message = "名称不能为空")
        private String name;
        private String shortName;
        private String mergerName;
        private String pinyin;
        @DecimalMin(value = "-180", message = "经度不能小于-180")
        @DecimalMax(value = "180", message = "经度不能大于180")
        private BigDecimal lng;
        @DecimalMin(value = "-90", message = "纬度不能小于-90")
        @DecimalMax(value = "90", message = "纬度不能大于90")
        private BigDecimal lat;

        public String getParentCode() {
            return parentCode;
        }

        public void setParentCode(String parentCode) {
            this.parentCode = parentCode;
        }

        public String getAreaCode() {
            return areaCode;
        }

        public void setAreaCode(String areaCode) {
            this.areaCode = areaCode;
        }

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }

        public String getCityCode() {
            return cityCode;
        }

        public void setCityCode(String cityCode) {
            this.cityCode = cityCode;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        public String getMergerName() {
            return mergerName;
        }

        public void setMergerName(String mergerName) {
            this.mergerName = mergerName;
        }

        public String getPinyin() {
            return pinyin;
        }

        public void setPinyin(String pinyin) {
            this.pinyin = pinyin;
        }

        public BigDecimal getLng() {
            return lng;
        }

        public void setLng(BigDecimal lng) {
            this.lng = lng;
        }

        public BigDecimal getLat() {
            return lat;
        }

        public void setLat(BigDecimal lat) {
            this.lat = lat;
        }
    }
}
