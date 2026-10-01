"""从同一组图元生成可编辑 drawio、SVG 及适合 Word 的高分辨率 PNG。"""

from pathlib import Path
from html import escape
import json
import math
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/thesis/figures/advisor-20260930"
FONT = Path("C:/Windows/Fonts/simsun.ttc")
SCALE = 3


class Figure:
    """保存绝对坐标图元，使三种导出格式保持布局一致。"""

    def __init__(self, name, title, width=1000, height=700):
        """初始化画布和图形元数据。"""
        self.name, self.title, self.width, self.height = name, title, width, height
        self.items = []

    def shape(self, kind, label, x, y, width, height, size=24, underline=False, background=False):
        """添加节点、属性、关系或无边框文字。"""
        node = dict(id="n" + str(len(self.items)), kind=kind, label=label, x=x, y=y,
                    w=width, h=height, size=size, underline=underline, background=background)
        self.items.append(node)
        return node

    def line(self, points, label="", at=None, size=22, dashed=False, arrow=False):
        """添加无箭头连接；基数标签使用独立文字节点避免压线。"""
        self.items.append(dict(id="e" + str(len(self.items)), kind="line", points=points, dashed=dashed, arrow=arrow))
        if label:
            x, y = at or points[len(points) // 2]
            self.shape("text", label, x, y, max(46, len(label) * size * .65), 30, size)

    def center(self, node):
        """返回节点中心。"""
        return node["x"] + node["w"] / 2, node["y"] + node["h"] / 2

    def attach(self, node, target):
        """求矩形、椭圆及菱形边界上的连接点。"""
        cx, cy = self.center(node)
        dx, dy = target[0] - cx, target[1] - cy
        if node["kind"] == "ellipse":
            rate = 1 / math.sqrt((dx / (node["w"] / 2)) ** 2 + (dy / (node["h"] / 2)) ** 2)
        elif node["kind"] == "diamond":
            rate = 1 / (abs(dx) / (node["w"] / 2) + abs(dy) / (node["h"] / 2))
        else:
            rate = 1 / max(abs(dx) / (node["w"] / 2), abs(dy) / (node["h"] / 2))
        return cx + dx * rate, cy + dy * rate

    def connect(self, left, right, left_card="", right_card=""):
        """连接两个节点并在端点附近显示关系基数。"""
        start = self.attach(left, self.center(right))
        end = self.attach(right, self.center(left))
        points = [start, end]
        if "diamond" in (left["kind"], right["kind"]):
            lx, ly = self.center(left)
            rx, ry = self.center(right)
            if abs(rx-lx) >= 90 and abs(ry-ly)>12:
                direction=1 if rx>lx else -1
                vertical=1 if ry>ly else -1
                if left['kind']=='rect':
                    start=(lx+direction*left['w']*.3,ly+vertical*left['h']/2)
                    end=(rx-direction*right['w']/2,ry)
                    rail=end[0]-direction*20
                    points=[start,(rail,start[1]+vertical*30),(rail,ry),end]
                else:
                    start=(lx+direction*left['w']/2,ly)
                    end=(rx,ry-vertical*right['h']/2)
                    points=[start,(rx,ly),end]
        self.line(points)
        segments=list(zip(points,points[1:]))
        orthogonal=[pair for pair in segments if pair[0][0]==pair[1][0] or pair[0][1]==pair[1][1]]
        segment=max(orthogonal or segments,key=lambda pair: abs(pair[1][0]-pair[0][0])+abs(pair[1][1]-pair[0][1]))
        a,b=segment
        for label in (left_card, right_card):
            if label:
                x,y=(a[0]+b[0])/2,(a[1]+b[1])/2
                horizontal=abs(b[0]-a[0])>=abs(b[1]-a[1])
                width=max(27,len(label)*11+9)
                if not horizontal and left_card and left['kind']=='rect':y=start[1]+(25 if end[1]>start[1] else -35)
                if not horizontal and right_card and right['kind']=='rect':y=end[1]+(-23 if end[1]>start[1] else 30)
                if not horizontal and right_card and right.get('has_attributes') and end[1]>start[1]:
                    y=(a[1]+right['y']-105)/2
                tx=x-width/2 if horizontal else x+5
                tx=min(tx,self.width-width-6)
                self.shape("text",label,tx,y-30 if horizontal else y-14,width,28,20)

    def entity(self, label, x, y, key, attribute=None, width=172):
        """建立实体和关键属性，主键属性加下划线。"""
        x=90 if x<200 else 420 if x<650 else 750
        width=160
        node = self.shape("rect", label, x, y, width, 60)
        node['has_attributes']=True
        cx=x+width/2
        primary = self.shape("ellipse", key, cx-157.5, y-86, 145, 48, 20, True)
        self.connect(node, primary)
        if attribute:
            attr = self.shape("ellipse", attribute, cx+12.5, y-86, 145, 48, 20)
            self.connect(node, attr)
        return node

    def relation(self, left, right, label, x, y, left_card="1", right_card="N", width=84):
        """以菱形表示联系，实体端标注可选性和基数。"""
        lx,ly=self.center(left)
        rx,ry=self.center(right)
        x=(lx+rx)/2-width/2
        if abs(ly-ry)<70:
            y=(ly+ry)/2-34
        else:
            upper,lower=(left,right) if ly<ry else (right,left)
            y=(upper['y']+upper['h']+lower['y']-(100 if lower.get('has_attributes') else 0))/2-34
        relation = self.shape("diamond", label, x, y, width, 68, 22)
        self.connect(left, relation, left_card)
        self.connect(relation, right, "", right_card)
        return relation

    def write(self):
        """绘制矢量、栅格和可编辑 XML，并记录验证清单。"""
        OUT.mkdir(parents=True, exist_ok=True)
        ordered = sorted(self.items, key=lambda item: 0 if item.get("background") else 1 if item["kind"] == "line" else 2)
        picture = Image.new("RGB", (self.width * SCALE, self.height * SCALE), "white")
        pen = ImageDraw.Draw(picture)
        svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" viewBox="0 0 {self.width} {self.height}">',
               f"<title>{escape(self.title)}</title>", '<rect width="100%" height="100%" fill="white"/>']
        mxfile = ET.Element("mxfile", host="app.diagrams.net", version="24.7.17")
        diagram = ET.SubElement(mxfile, "diagram", id=self.name, name=self.title)
        model = ET.SubElement(diagram, "mxGraphModel", dx=str(self.width), dy=str(self.height), grid="1", gridSize="10",
                              page="1", pageScale="1", pageWidth=str(self.width), pageHeight=str(self.height), math="0", shadow="0")
        root = ET.SubElement(model, "root")
        ET.SubElement(root, "mxCell", id="0")
        ET.SubElement(root, "mxCell", id="1", parent="0")
        for item in ordered:
            if item["kind"] == "line":
                points = item["points"]
                scaled = [(round(x * SCALE), round(y * SCALE)) for x, y in points]
                pen.line(scaled, fill="black", width=4)
                svg.append('<polyline points="' + " ".join(f"{x},{y}" for x, y in points) + '" fill="none" stroke="black" stroke-width="1.4"/>')
                if item.get('arrow'):
                    tip=points[-1]
                    dx,dy=tip[0]-points[-2][0],tip[1]-points[-2][1]
                    length=math.hypot(dx,dy)
                    ux,uy=dx/length,dy/length
                    triangle=[tip,(tip[0]-ux*12-uy*5,tip[1]-uy*12+ux*5),(tip[0]-ux*12+uy*5,tip[1]-uy*12-ux*5)]
                    pen.polygon([(round(x*SCALE),round(y*SCALE)) for x,y in triangle],fill='black')
                    svg.append('<polygon points="'+' '.join(f'{x},{y}' for x,y in triangle)+'" fill="black"/>')
                cell = ET.SubElement(root, "mxCell", id=item["id"], edge="1", parent="1",
                                     style="endArrow="+('block;endFill=1' if item.get('arrow') else 'none')+";startArrow=none;strokeColor=#000000;strokeWidth=1.4;rounded=0;")
                geo = ET.SubElement(cell, "mxGeometry", relative="1", **{"as": "geometry"})
                ET.SubElement(geo, "mxPoint", x=str(points[0][0]), y=str(points[0][1]), **{"as": "sourcePoint"})
                ET.SubElement(geo, "mxPoint", x=str(points[-1][0]), y=str(points[-1][1]), **{"as": "targetPoint"})
                if len(points) > 2:
                    array = ET.SubElement(geo, "Array", **{"as": "points"})
                    for x, y in points[1:-1]:
                        ET.SubElement(array, "mxPoint", x=str(x), y=str(y))
                continue
            x, y, w, h = item["x"], item["y"], item["w"], item["h"]
            kind = item["kind"]
            box = tuple(round(value * SCALE) for value in (x, y, x + w, y + h))
            attrs = 'fill="white" stroke="black" stroke-width="1.4"'
            if kind == "ellipse":
                pen.ellipse(box, fill="white", outline="black", width=4)
                svg.append(f'<ellipse cx="{x+w/2}" cy="{y+h/2}" rx="{w/2}" ry="{h/2}" {attrs}/>')
            elif kind == "diamond":
                points = [(x + w / 2, y), (x + w, y + h / 2), (x + w / 2, y + h), (x, y + h / 2)]
                pen.polygon([(round(a*SCALE), round(b*SCALE)) for a,b in points], fill="white", outline="black", width=4)
                svg.append('<polygon points="' + " ".join(f"{a},{b}" for a,b in points) + f'" {attrs}/>')
            elif kind == "actor":
                cx = x + w / 2
                pen.ellipse((round((cx-13)*SCALE), round(y*SCALE), round((cx+13)*SCALE), round((y+26)*SCALE)), outline="black", width=4)
                svg.append(f'<circle cx="{cx}" cy="{y+13}" r="13" fill="white" stroke="black" stroke-width="1.4"/>')
                strokes = [[(cx,y+26),(cx,y+59)],[(cx-25,y+38),(cx+25,y+38)],[(cx,y+59),(cx-23,y+85)],[(cx,y+59),(cx+23,y+85)]]
                for stroke in strokes:
                    pen.line([(round(a*SCALE),round(b*SCALE)) for a,b in stroke],fill="black",width=4)
                    svg.append('<polyline points="' + " ".join(f"{a},{b}" for a,b in stroke) + '" fill="none" stroke="black" stroke-width="1.4"/>')
            elif kind == "rect":
                pen.rectangle(box, fill="white", outline="black", width=4)
                svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" {attrs}/>')
            elif kind == "text":
                pass
            style = {"rect":"rounded=0;", "ellipse":"ellipse;", "diamond":"rhombus;", "actor":"shape=umlActor;", "text":"text;strokeColor=none;"}[kind]
            style += f"whiteSpace=wrap;html=1;fillColor=#ffffff;fontColor=#000000;fontFamily=SimSun;fontSize={item['size']};align=center;verticalAlign=middle;spacing=0;"
            if kind != "text":
                style += "strokeColor=#000000;strokeWidth=1.4;"
            else:
                style += "fillColor=none;"
            label = escape(item["label"]).replace("\n", "<br>")
            if item["underline"]:
                label = "<u>" + label + "</u>"
            cell = ET.SubElement(root, "mxCell", id=item["id"], value=label, vertex="1", parent="1", style=style)
            ET.SubElement(cell, "mxGeometry", x=str(x), y=str(y), width=str(w), height=str(h), **{"as":"geometry"})
            if item["label"]:
                font = ImageFont.truetype(str(FONT), item["size"] * SCALE)
                lines = item["label"].split("\n")
                line_height = item["size"] * 1.3
                top = y + (h - line_height * len(lines)) / 2
                for offset, line in enumerate(lines):
                    text_width = pen.textlength(line, font=font)
                    assert text_width <= w*SCALE - 7*SCALE, f"文字超宽：{self.name} {line}"
                    tx = (x + w / 2) * SCALE - text_width / 2
                    ty = (top + offset * line_height) * SCALE
                    pen.text((tx,ty),line,font=font,fill="black")
                    baseline = top + offset * line_height + item["size"] * .88
                    decoration = ' text-decoration="underline"' if item["underline"] else ''
                    svg.append(f'<text x="{x+w/2}" y="{baseline}" text-anchor="middle" font-family="SimSun,serif" font-size="{item["size"]}"{decoration}>{escape(line)}</text>')
                    if item["underline"]:
                        pen.line([(tx, ty + item["size"]*SCALE*1.05), (tx+text_width, ty+item["size"]*SCALE*1.05)], fill="black", width=3)
        svg.append("</svg>")
        (OUT / (self.name + ".svg")).write_text("\n".join(svg), encoding="utf-8")
        ET.indent(mxfile)
        ET.ElementTree(mxfile).write(OUT / (self.name + ".drawio"), encoding="utf-8", xml_declaration=True)
        picture.save(OUT / (self.name + ".png"), dpi=(450,450))
        return {"name":self.name,"title":self.title,"width":self.width,"height":self.height,"shapes":len(self.items)}


def use_case(name, actor, cases):
    """绘制单角色用例图，登录要求在正文中作为前置条件说明。"""
    height = 100 + len(cases) * 80
    fig = Figure(name, actor + "用例图", 1000, height)
    fig.shape("rect", "", 260, 12, 720, height - 24, background=True)
    fig.shape("text", "道路运输企业在线培训系统", 370, 25, 500, 42, 26)
    cy = height / 2
    fig.shape("actor", "", 100, cy-45, 60, 85)
    fig.shape("text", actor, 35, cy+54, 190, 40, 26)
    for index, label in enumerate(cases):
        node = fig.shape("ellipse", label, 412, 85 + index*80, 425, 54, 25)
        fig.line([(160,cy), (node["x"],node["y"]+node["h"]/2)])
    return fig


def function_tree():
    """以两列纵向功能树保持 A4 正文图中文字清晰。"""
    fig = Figure("function-structure", "系统功能结构图", 1000, 850)
    fig.shape("rect", "道路运输企业在线培训系统", 230, 15, 540, 62, 28)
    branches = [
        ("基础管理", "组织与行政区域\n部门、人员与车辆\n角色权限与登记照", 45,145),
        ("课程课件", "课程及规则维护\n视频上传与课件编排\n受控媒体访问", 545,145),
        ("培训计划", "课程及人员分配\n计划发布与快照\n周期及结业状态", 45,315),
        ("学习与身份核验", "签到、播放与签退\n学时确认与断线恢复\n随机抽验及核验记录", 545,315),
        ("题库考试", "题库与试卷维护\n学习完成后开考\n答案保存与服务端判分", 45,485),
        ("档案统计", "本人培训档案\n企业学习明细及照片\n培训进度与结果统计", 545,485),
        ("个人账户", "登录与密码维护\n本人资料与登记照\n按权限进入工作台", 295,655),
    ]
    fig.line([(500,77),(500,625)])
    for label, detail, x,y in branches:
        fig.shape("rect",label,x,y,410,45,25)
        fig.shape("rect",detail,x,y+45,410,100,23)
        end = (x+410,y+22.5) if x<295 else (x,y+22.5)
        if x==295:
            fig.line([(500,625),(500,y)])
        else:
            fig.line([(500,y+22.5),end])
    return fig


def overview():
    """总览只展示主线实体与联系，属性在分域图展开。"""
    fig = Figure("er-overview", "核心业务概念 ER 总览", 1000, 870)
    nodes = {}
    for key,label,x,y in [("org","企业",40,45),("user","学员",400,45),("vehicle","车辆",790,45),
                           ("plan","培训计划",40,275),("task","参训任务",400,275),
                           ("session","学习会话",40,520),("progress","课程进度",400,520),("exam","考试记录",790,520),
                           ("event","受理事件",40,760),("face","抽验任务",400,760),("attempt","抽验提交",790,760)]:
        nodes[key]=fig.shape("rect",label,x,y,165,58,25)
    fig.relation(nodes["org"],nodes["user"],"归属",252,40,"1","0..N")
    fig.relation(nodes["user"],nodes["vehicle"],"绑定",640,40,"0..N","0..1")
    fig.relation(nodes["plan"],nodes["task"],"分配",252,270,"1","0..N")
    fig.relation(nodes["task"],nodes["user"],"参训",430,156,"0..N","1")
    fig.relation(nodes["task"],nodes["session"],"产生",245,403,"1","0..N")
    fig.relation(nodes["task"],nodes["progress"],"累计",430,406,"1","0..N")
    fig.relation(nodes["task"],nodes["exam"],"考试",660,406,"1","0..1")
    fig.relation(nodes["session"],nodes["event"],"记录",70,640,"1","0..N")
    fig.relation(nodes["session"],nodes["face"],"触发",240,643,"1","0..N")
    fig.relation(nodes["face"],nodes["attempt"],"提交",635,755,"1","0..N")
    return fig


def admin_er():
    """管理域按身份、授权和组织关系建模，关联表在数据字典中映射。"""
    fig=Figure("er-admin","管理域概念 ER 图",1000,1000)
    org=fig.entity("组织／部门",60,130,"组织编号","组织性质")
    user=fig.entity("用户",425,130,"用户编号","姓名")
    vehicle=fig.entity("车辆",785,130,"车辆编号","车牌号码")
    area=fig.entity("行政区域",60,510,"区域编号","行政代码")
    role=fig.entity("角色",425,510,"角色编号","角色名称")
    permission=fig.entity("权限",785,510,"权限编号","权限编码")
    fig.relation(org,user,"归属",270,125,"1","0..N")
    fig.relation(user,vehicle,"绑定",640,125,"0..N","0..1")
    fig.relation(org,area,"辖区",90,355,"0..N","0..1")
    fig.relation(user,role,"授予",455,355,"M","N")
    fig.relation(role,permission,"授权",640,505,"M","N")
    fig.shape("text","M:N 联系通过用户角色、角色权限表实现。",75,740,850,45,24)
    fig.shape("text","组织用户表保存用户的主部门归属；组织层级由父节点标识表达。",40,795,920,45,23)
    fig.shape("text","行政区域仅建立辖区模型；跨企业监管查询尚未实现。",50,850,900,45,23)
    fig.shape("text","下划线表示标识属性；连线表示逻辑关系。",100,905,800,45,23)
    return fig


def training_er():
    """以计划、课程快照和考试展示培训域主要概念关系。"""
    fig=Figure("er-training","培训与考试域概念 ER 图",1000,1270)
    course=fig.entity("课程",40,130,"课程编号","课程名称")
    ware=fig.entity("视频课件",405,130,"课件编号","视频时长")
    storage=fig.entity("存储对象",775,130,"对象编号","对象类型")
    plan=fig.entity("培训计划",40,465,"计划编号","计划状态")
    snap=fig.entity("计划课程",405,465,"计划课程编号","规定时长")
    wsnap=fig.entity("课件快照",775,465,"快照编号","顺序")
    task=fig.entity("参训任务",40,800,"任务编号","完成状态")
    paper=fig.entity("试卷",405,800,"试卷编号","及格分")
    paperq=fig.entity("试卷题目",775,800,"试卷题目编号","答案快照")
    record=fig.shape("rect","考试记录",90,1110,160,60)
    answer=fig.shape("rect","考试答案",420,1110,160,60)
    question=fig.shape("rect","题库题目",750,1110,160,60)
    fig.relation(course,ware,"包含",258,125,"1","0..N")
    fig.relation(ware,storage,"引用",622,125,"0..N","1")
    fig.relation(plan,snap,"冻结",258,460,"1","0..N")
    fig.relation(snap,wsnap,"冻结",622,460,"1","0..N")
    fig.relation(course,snap,"来源",258,307,"1","0..N")
    fig.relation(plan,task,"分配",70,660,"1","0..N")
    fig.relation(plan,paper,"使用",257,660,"0..N","0..1")
    fig.relation(paper,paperq,"固化",622,795,"1","0..N")
    fig.relation(task,record,"参加",70,982,"1","0..1")
    fig.relation(record,answer,"作答",258,1105,"1","0..N")
    fig.relation(paperq,question,"来源",805,982,"0..N","1")
    fig.shape("text","发布后课程规则与课件目录固定；答案按试卷题目快照判分。",40,1210,920,40,23)
    return fig


def learning_er():
    """区别会话、事件、进度及可零次提交的抽验任务。"""
    fig=Figure("er-learning","学习监管域概念 ER 图",1000,1190)
    session=fig.entity("学习会话",40,130,"会话编号","会话状态")
    event=fig.entity("学习事件",405,130,"事件编号","事件序号")
    task=fig.entity("参训任务",775,130,"任务编号","完成状态")
    face=fig.entity("抽验任务",40,490,"抽验任务编号","截止时间")
    log=fig.entity("抽验提交",405,490,"提交编号","核验结果")
    progress=fig.entity("课程进度",775,490,"进度编号","有效学时")
    photo=fig.entity("照片对象",405,880,"对象编号","所有者")
    ware=fig.entity("课件进度",775,880,"课件进度编号","确认位置")
    fig.entity("发件记录",40,880,"发件编号","事件标识")
    fig.relation(session,event,"受理",258,125,"1","0..N")
    fig.relation(session,face,"触发",70,338,"1","0..N")
    fig.relation(face,log,"提交",258,485,"1","0..N")
    fig.relation(log,photo,"引用",435,698,"0..N","0..1")
    fig.relation(progress,ware,"汇总",805,698,"1","0..N")
    fig.relation(task,progress,"累计",805,338,"1","0..N")
    fig.shape("text","发件记录与进度在同一本地事务中保存，按事件标识投递。",40,1050,920,42,23)
    fig.shape("text","会话签到、签退也可引用照片；历史无照片记录允许为空。",40,1100,920,42,23)
    return fig


def architecture():
    """保留原架构职责，调整公共支撑注释与消息通路线条的间距。"""
    fig=Figure('architecture','系统总体架构图',1200,900)
    for label,x,y,w,h in [
        ('Vue 管理端与学员端',400,20,400,66),('Gateway 统一入口',400,145,400,66),
        ('Web API 页面接口与聚合',80,270,440,66),('Realtime 实时消息接入',780,270,360,66),
        ('Admin 管理服务\n组织、人员、权限、车辆',20,440,330,96),
        ('Training 培训服务\n课程、计划、考试',435,440,330,96),
        ('Learning 学习服务\n会话、学时、抽验\n内嵌人脸适配器',850,440,330,130),
        ('管理库',35,630,300,62),('培训库',450,630,300,62),('学习库与 Outbox',865,630,300,62),
        ('RabbitMQ 学习结果事件',440,765,335,62)]:
        fig.shape('rect',label,x,y,w,h,24)
    fig.shape('text','HTTP / WebSocket',660,95,300,35,23)
    for points in [
        [(600,86),(600,145)],[(490,211),(490,240),(300,240),(300,270)],
        [(710,211),(710,240),(960,240),(960,270)],
        [(180,336),(180,395),(185,395),(185,440)],
        [(330,336),(330,390),(600,390),(600,440)],
        [(500,336),(500,360),(920,360),(920,440)],
        [(960,336),(960,400),(1100,400),(1100,440)],
        [(185,536),(185,630)],[(600,536),(600,630)],[(1015,570),(1015,630)],
        [(1150,692),(1150,798),(775,798)],[(440,798),(385,798),(385,488),(435,488)]]:
        fig.line(points,arrow=True)
    fig.shape('text','Dubbo',82,365,90,32,22)
    fig.shape('text','Dubbo',380,350,90,32,22)
    fig.shape('text','Dubbo',650,365,90,32,22)
    fig.shape('text','Dubbo',987,354,95,32,22)
    fig.shape('text','公共支撑：私有 OSS 媒体；Redis 认证授权缓存；Nacos 注册配置',10,850,1180,40,23)
    return fig


def learning_flow():
    """保留学习消息处理步骤，将重复请求与消息投递注释放在线旁。"""
    fig=Figure('learning-flow','学习进度受理与结果传播流程',1100,740)
    left=['学员发送进度消息','核验身份、任务与会话绑定','查询请求标识及检查序号','核验学习状态与计时条件','事务保存学时、事件与 Outbox']
    for i,label in enumerate(left):
        fig.shape('rect',label,35,20+i*130,430,65,24)
        if i:fig.line([(250,85+(i-1)*130),(250,20+i*130)],arrow=True)
    for label,y in [('重复请求返回历史确认结果',20),('向浏览器返回确认学时',280),('发送器发布学习结果事件',410),('培训服务去重并更新任务',540)]:
        fig.shape('rect',label,675,y,390,65,24)
    fig.line([(465,312.5),(575,312.5),(575,52.5),(675,52.5)],arrow=True)
    fig.shape('text','已受理\n请求',477,145,90,62,23)
    fig.line([(465,572.5),(600,572.5),(600,312.5),(675,312.5)],arrow=True)
    fig.line([(600,442.5),(675,442.5)],arrow=True)
    fig.line([(870,475),(870,540)],arrow=True)
    fig.shape('text','RabbitMQ\n至少一次投递',895,477,180,60,20)
    fig.shape('text','身份、状态或序号不符合要求时拒绝；超间隔不补记断线时长。',35,670,1030,42,23)
    return fig


def main():
    """生成八幅新增图，并修复两幅原图的注释压线。"""
    figures=[
        use_case("usecase-platform","平台管理员",["维护组织档案","启停组织及重置管理员密码","维护行政区域字典","查看管理工作台","维护本人资料及登记照"]),
        use_case("usecase-enterprise","企业管理员",["管理部门、人员及登记照","维护车辆台账及人员绑定","管理角色与功能授权","维护课程及视频课件","维护题库与试卷","编排并发布培训计划","查询培训统计与过程档案","维护本人资料及登记照"]),
        use_case("usecase-student","学员",["查看本人培训任务","签到及观看课程","暂停、恢复和补足学时","提交人脸核验及签退","学习完成后参加考试","查询本人档案与成绩","维护本人资料及登记照"]),
        function_tree(),overview(),admin_er(),training_er(),learning_er(),architecture(),learning_flow()]
    manifest=[figure.write() for figure in figures]
    (OUT/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(manifest,ensure_ascii=False))


if __name__=="__main__":
    main()
