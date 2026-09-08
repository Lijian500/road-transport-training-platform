"""生成自制H.264演示视频；仅含流程文字、计时器和动画，不使用外部图片或人物素材。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont


def main():
    """通过FFmpeg编码固定帧率MP4，并记录可复现参数与内容摘要。"""
    sys.stdout.reconfigure(encoding="utf-8")
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="tmp/media/training-demo.mp4")
    parser.add_argument("--seconds", type=int, default=100)
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"))
    parser.add_argument("--font", default="C:/Windows/Fonts/msyh.ttc")
    args = parser.parse_args()
    if not 60 <= args.seconds <= 180:
        parser.error("视频时长必须为60至180秒")
    if not args.ffmpeg or not Path(args.ffmpeg).is_file():
        parser.error("请用--ffmpeg指定本机FFmpeg可执行文件")
    if not Path(args.font).is_file():
        parser.error("请用--font指定支持中文的本机字体文件")
    output = (root / args.output).resolve()
    if output.exists() or output.with_suffix(".json").exists():
        parser.error("输出文件已存在，请选择新的--output，不覆盖已有证据")
    output.parent.mkdir(parents=True, exist_ok=True)
    width, height, fps = 960, 540, 20
    title_font = ImageFont.truetype(args.font, 38)
    text_font = ImageFont.truetype(args.font, 23)
    timer_font = ImageFont.truetype(args.font, 55)
    background = Image.new("RGB", (width, height), "#112033")
    canvas = ImageDraw.Draw(background)
    canvas.text((64, 48), "道路运输培训平台 · 演示视频", font=title_font, fill="#edf5ff")
    canvas.text((64, 116), "签到 → 视频学习 → 抽验 → 签退 → 考试", font=text_font, fill="#a8c2de")
    canvas.text((64, 168), "自制流程演示素材，不含人物与外部图片", font=text_font, fill="#a8c2de")
    canvas.rounded_rectangle((64, 242, 896, 426), radius=22, fill="#1d344e")
    canvas.text((64, 480), "连续画面用于检查解码、播放位置与服务端有效学时", font=text_font, fill="#a8c2de")
    command = [str(args.ffmpeg), "-hide_banner", "-loglevel", "error", "-n",
               "-f", "rawvideo", "-pixel_format", "rgb24", "-video_size", f"{width}x{height}",
               "-framerate", str(fps), "-i", "pipe:0", "-an", "-c:v", "libx264",
               "-preset", "veryfast", "-crf", "24", "-pix_fmt", "yuv420p",
               "-movflags", "+faststart", str(output)]
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stderr=subprocess.PIPE,
                               stdout=subprocess.DEVNULL,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    try:
        for frame in range(args.seconds * fps):
            picture = background.copy()
            canvas = ImageDraw.Draw(picture)
            seconds = frame / fps
            canvas.text((100, 270), f"{seconds:06.2f} / {args.seconds} s", font=timer_font, fill="#eff7ff")
            left = 100 + int(frame / (args.seconds * fps - 1) * 720)
            canvas.line((100, 388, 850, 388), fill="#506883", width=6)
            canvas.ellipse((left - 13, 375, left + 13, 401), fill="#42dbb2")
            process.stdin.write(picture.tobytes())
        process.stdin.close()
        error = process.stderr.read().decode("utf-8", errors="replace")
        if process.wait() != 0:
            raise RuntimeError(f"FFmpeg编码失败：{error}")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()
        process.stderr.close()
    evidence = {"source": "本仓库脚本自制流程文字与动画，无人物、无外部图片、无音轨",
                "durationSeconds": args.seconds, "width": width, "height": height,
                "framesPerSecond": fps, "codec": "H.264", "pixelFormat": "yuv420p",
                "fastStart": True, "bytes": output.stat().st_size,
                "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}
    output.with_suffix(".json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"video": str(output), **evidence}, ensure_ascii=False))


if __name__ == "__main__":
    main()
