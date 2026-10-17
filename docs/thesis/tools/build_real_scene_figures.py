"""从用户真实截图生成论文插图；仅裁剪、遮挡和拼接，不重绘界面。"""

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def digest(data):
    """计算证据文件或像素的 SHA-256。"""
    return hashlib.sha256(data).hexdigest()


def load_source(source_dir, name, crop=None, masks=None, label=None, preserves=None):
    """读取源图，按需遮挡敏感区域后按原始坐标裁剪。"""
    source = source_dir / name
    source_bytes = source.read_bytes()
    with Image.open(source) as opened:
        image = opened.convert("RGB")
    original_size = list(image.size)
    masks = masks or []
    original = image.copy()
    draw = ImageDraw.Draw(image)
    for box in masks:
        # 半开区间与 Pillow.crop 一致；纯色覆盖不保留人脸或视频像素。
        draw.rectangle((box[0], box[1], box[2] - 1, box[3] - 1), fill=(166, 166, 166))
    for box in preserves or []:
        # 只原位保留控制按钮等原始像素，不把半透明控制条下的视频帧带回。
        image.paste(original.crop(box), (box[0], box[1]))
    crop = crop or [0, 0, image.width, image.height]
    image = image.crop(crop)
    return image, {
        "source_file": name,
        "source_sha256": digest(source_bytes),
        "source_size_px": original_size,
        "crop_box_xyxy": crop,
        "irreversible_solid_gray_masks_xyxy": masks,
        "preserved_native_ui_regions_xyxy": preserves or [],
        "group_label": label,
        "resize": "none",
    }


def add_label(image, label, font):
    """在截图外增加组图标签，保持原截图内容不变。"""
    band = font.size + 18
    output = Image.new("RGB", (image.width, image.height + band), "white")
    ImageDraw.Draw(output).text((8, 6), label, fill=(35, 35, 35), font=font)
    output.paste(image, (0, band))
    return output


def compose(parts, direction, font):
    """沿指定方向拼接原始截图，留白分隔并保持原像素比例。"""
    labelled = [add_label(image, info["group_label"], font) if info["group_label"] else image
                for image, info in parts]
    gap, margin = 16, 10
    if direction == "horizontal":
        width = sum(image.width for image in labelled) + gap * (len(parts) - 1)
        height = max(image.height for image in labelled)
    else:
        width = max(image.width for image in labelled)
        height = sum(image.height for image in labelled) + gap * (len(parts) - 1)
    output = Image.new("RGB", (width + margin * 2, height + margin * 2), "white")
    offset = margin
    for image in labelled:
        if direction == "horizontal":
            output.paste(image, (offset, margin))
            offset += image.width + gap
        else:
            output.paste(image, (margin, offset))
            offset += image.height + gap
    return output


def save_figure(output_dir, name, parts, font, direction="vertical", width_cm=15.5,
                note="", embedded=True):
    """保存无 EXIF 的 RGB PNG，并记录源图处理范围和成品哈希。"""
    if len(parts) == 1 and not parts[0][1]["group_label"]:
        image = parts[0][0]
    else:
        image = compose(parts, direction, font)
    output = output_dir / name
    # 新建 RGB 图像，避免把原图的 EXIF、ICC 或文本元信息带入仓库。
    clean = Image.frombytes("RGB", image.size, image.tobytes())
    clean.save(output, format="PNG", optimize=True)
    return {
        "output_file": name,
        "output_sha256": digest(output.read_bytes()),
        "output_rgb_pixels_sha256": digest(clean.tobytes()),
        "output_size_px": list(clean.size),
        "preferred_width_cm": width_cm,
        "height_at_preferred_width_cm": round(width_cm * clean.height / clean.width, 2),
        "composition": direction if len(parts) > 1 else "single",
        "embedded_in_thesis": embedded,
        "note": note,
        "sources": [info for _, info in parts],
    }


def main():
    """按本次导师要求生成脱敏功能截图和可复核清单。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", required=True, type=Path)
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "figures/real-scenes-20261017")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    font = ImageFont.truetype(str(font_path), 26)
    smaller_font = ImageFont.truetype(str(font_path), 22)
    figures = []

    def part(name, crop=None, masks=None, label=None, preserves=None):
        """从只读的外部目录载入单个截图片段。"""
        return load_source(args.source_dir, name, crop, masks, label, preserves)

    def save(name, parts, **kwargs):
        """使用统一输出目录保存单图或组图。"""
        figures.append(save_figure(args.output_dir, name, parts, **kwargs))

    save("course.png", [
        part("课件设置1.png", [16, 0, 1563, 377], label="(a) 课程规则"),
        part("课件设置2.png", [2, 406, 1548, 783], label="(b) 视频课件"),
    ], font=font, note="分别裁取规则和视频课件表，排除课程封面。两张截图为不同位置的真实页面，不作同一时刻规则一致的推断。")

    save("plan.png", [
        part("计划基础信息1.png", [0, 0, 1535, 638], label="(a) 基础信息与抽验配置"),
        part("计划基础信息二.png", label="(b) 发布课程与学员快照"),
    ], font=font, note="配置和发布快照均按截图原样保留。")

    # 用户已在原图中打好马赛克，这三幅图仅裁剪或拼接，不再扩大遮挡。
    save("sign-in.png", [part("人脸签到1.png", [11, 15, 499, 456])], font=font, width_cm=9,
         note="签到摄像头预览，沿用用户原图中已有的人脸马赛克，不追加遮挡；拍照按钮仍可见，不能据此声称签到验证已成功。")

    save("video.png", [part("视频学习2.png")], font=font,
         note="直接采用用户已对版权部分打马赛克的原图，完整保留视频画面、播放器控件、学时、课件目录和底部暂停提示，不追加遮挡。截图中播放器为暂停状态，不能推断播放位置与累计学时的定量关系。")
    save("video-alternate.png", [part("视频学习1.png", masks=[[338, 230, 1296, 770]])],
         font=font, embedded=False,
         note="备用单图，不嵌入本次论文；版权画面整体覆盖，保留有效学时、目录和正常签退按钮。")

    save("face-check.png", [
        part("学习触发抽验.png", [12, 15, 602, 559], label="(a) 开启摄像头前"),
        part("抽验拍照1.png", [9, 16, 590, 722],
             label="(b) 摄像头预览"),
    ], font=smaller_font, direction="horizontal",
         note="两幅真实截图并排展示抽验提示和摄像头预览，沿用用户已有的人脸马赛克，不追加遮挡；保留各自倒计时、3/3剩余提交次数及拍照按钮，不能表述为已经提交或核验成功。")

    save("paper.png", [part("创建试卷二.png", [12, 15, 740, 661])], font=font, width_cm=13,
         note="试卷草稿配置及手工选题下拉框，保留截图已显示的选题内容和参数。")
    save("task-detail.png", [part("学员的计划学习详情页面.png", [0, 0, 1317, 835])],
         font=font, note="学员计划详情、考试入口条件、课程有效学时与课件完成情况。")
    save("record.png", [part("学员学习档案.png", [8, 0, 1712, 819],
         masks=[[1131, 585, 1207, 661], [1241, 585, 1317, 661], [1131, 684, 1207, 760]])],
         font=font, note="三张签到/签退照片缩略图整块纯灰遮挡；保留已签退、已终止及抽验超时等原始状态。")
    save("statistics.png", [part("管理员-培训统计页面.png")], font=font,
         note="保留原始统计卡片和计划统计表，不据此新增实验样本规模或完成率结论。")

    manifest = {
        "batch": "20261017",
        "source_directory": str(args.source_dir),
        "processing": "Pillow 确定性裁剪及原像素拼接；图5-4、5-5、5-6沿用用户原图已有马赛克，不追加遮挡；档案缩略图及未嵌入的备用视频仍保留原处理。不使用生成式图像，不修改数字、状态、姓名或按钮；坐标均为源图像左上原点的半开区间。",
        "privacy": "仅使用用户已脱敏的截图，不保留未打马赛克的人脸照片；成品 PNG 为无 EXIF、无文本元信息的 RGB 图像。",
        "figures": figures,
    }
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"generated": len(figures), "output_dir": str(args.output_dir)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
