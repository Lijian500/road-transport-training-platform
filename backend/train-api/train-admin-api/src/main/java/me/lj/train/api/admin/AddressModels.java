package me.lj.train.api.admin;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 行政地址RPC请求及响应模型。
 */
public final class AddressModels {

    private AddressModels() {
    }

    /**
     * 新增行政地址命令，层级由上级地址自动计算。
     */
    public static final class CreateAddressCommand implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String parentCode;
        private final String areaCode;
        private final String zipCode;
        private final String cityCode;
        private final String name;
        private final String shortName;
        private final String mergerName;
        private final String pinyin;
        private final BigDecimal lng;
        private final BigDecimal lat;

        public CreateAddressCommand(
                String parentCode,
                String areaCode,
                String zipCode,
                String cityCode,
                String name,
                String shortName,
                String mergerName,
                String pinyin,
                BigDecimal lng,
                BigDecimal lat) {
            this.parentCode = parentCode;
            this.areaCode = areaCode;
            this.zipCode = zipCode;
            this.cityCode = cityCode;
            this.name = name;
            this.shortName = shortName;
            this.mergerName = mergerName;
            this.pinyin = pinyin;
            this.lng = lng;
            this.lat = lat;
        }

        public String getParentCode() {
            return parentCode;
        }

        public String getAreaCode() {
            return areaCode;
        }

        public String getZipCode() {
            return zipCode;
        }

        public String getCityCode() {
            return cityCode;
        }

        public String getName() {
            return name;
        }

        public String getShortName() {
            return shortName;
        }

        public String getMergerName() {
            return mergerName;
        }

        public String getPinyin() {
            return pinyin;
        }

        public BigDecimal getLng() {
            return lng;
        }

        public BigDecimal getLat() {
            return lat;
        }
    }

    /**
     * 编辑行政地址命令，不允许修改上级和层级。
     */
    public static final class UpdateAddressCommand implements Serializable {

        private static final long serialVersionUID = 1L;

        private final Long id;
        private final String areaCode;
        private final String zipCode;
        private final String cityCode;
        private final String name;
        private final String shortName;
        private final String mergerName;
        private final String pinyin;
        private final BigDecimal lng;
        private final BigDecimal lat;

        public UpdateAddressCommand(
                Long id,
                String areaCode,
                String zipCode,
                String cityCode,
                String name,
                String shortName,
                String mergerName,
                String pinyin,
                BigDecimal lng,
                BigDecimal lat) {
            this.id = id;
            this.areaCode = areaCode;
            this.zipCode = zipCode;
            this.cityCode = cityCode;
            this.name = name;
            this.shortName = shortName;
            this.mergerName = mergerName;
            this.pinyin = pinyin;
            this.lng = lng;
            this.lat = lat;
        }

        public Long getId() {
            return id;
        }

        public String getAreaCode() {
            return areaCode;
        }

        public String getZipCode() {
            return zipCode;
        }

        public String getCityCode() {
            return cityCode;
        }

        public String getName() {
            return name;
        }

        public String getShortName() {
            return shortName;
        }

        public String getMergerName() {
            return mergerName;
        }

        public String getPinyin() {
            return pinyin;
        }

        public BigDecimal getLng() {
            return lng;
        }

        public BigDecimal getLat() {
            return lat;
        }
    }

    /**
     * 行政地址节点。
     */
    public static final class AddressView implements Serializable {

        private static final long serialVersionUID = 1L;

        private final Long id;
        private final int level;
        private final String parentCode;
        private final String areaCode;
        private final String zipCode;
        private final String cityCode;
        private final String name;
        private final String shortName;
        private final String mergerName;
        private final String pinyin;
        private final BigDecimal lng;
        private final BigDecimal lat;
        private final boolean hasChildren;

        public AddressView(
                Long id,
                int level,
                String parentCode,
                String areaCode,
                String zipCode,
                String cityCode,
                String name,
                String shortName,
                String mergerName,
                String pinyin,
                BigDecimal lng,
                BigDecimal lat,
                boolean hasChildren) {
            this.id = id;
            this.level = level;
            this.parentCode = parentCode;
            this.areaCode = areaCode;
            this.zipCode = zipCode;
            this.cityCode = cityCode;
            this.name = name;
            this.shortName = shortName;
            this.mergerName = mergerName;
            this.pinyin = pinyin;
            this.lng = lng;
            this.lat = lat;
            this.hasChildren = hasChildren;
        }

        public Long getId() {
            return id;
        }

        public int getLevel() {
            return level;
        }

        public String getParentCode() {
            return parentCode;
        }

        public String getAreaCode() {
            return areaCode;
        }

        public String getZipCode() {
            return zipCode;
        }

        public String getCityCode() {
            return cityCode;
        }

        public String getName() {
            return name;
        }

        public String getShortName() {
            return shortName;
        }

        public String getMergerName() {
            return mergerName;
        }

        public String getPinyin() {
            return pinyin;
        }

        public BigDecimal getLng() {
            return lng;
        }

        public BigDecimal getLat() {
            return lat;
        }

        public boolean isHasChildren() {
            return hasChildren;
        }
    }
}
