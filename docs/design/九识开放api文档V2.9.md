## 九识开放 API 文档

<table><tr><td>日期</td><td>版本</td><td>说明</td><td>作者</td></tr><tr><td>2024/07/01</td><td>1.0</td><td>新增文档</td><td>范亚运</td></tr><tr><td>2024/07/22</td><td>1.1</td><td>新增车辆的语音和点阵屏显示</td><td>范亚运</td></tr><tr><td>2024/07/23</td><td>1.2</td><td>新增添加装单任务并出发接口</td><td>李鹏飞</td></tr><tr><td>2024/08/07</td><td>1.3</td><td>新增重启、关机接口</td><td>李鹏飞</td></tr><tr><td>2024/09/11</td><td>1.4</td><td>新增跑圈接口</td><td>肖伟</td></tr><tr><td>2024/09/26</td><td>1.5</td><td>任务增加限速字段,增加了路线预览接口,增加了查询车辆任务路线接口</td><td>范亚运</td></tr><tr><td>2024/10/15</td><td>1.6</td><td>通用指令增加急停和恢复</td><td>肖伟</td></tr><tr><td>2024/11/06</td><td>1.7</td><td>通用指令增加 tbox 上电</td><td>倪文斌</td></tr><tr><td>2025/03/10</td><td>1.8</td><td>车辆增加车型参数</td><td>肖伟</td></tr><tr><td>2025/03/17</td><td>1.9</td><td>通用指令部分参数变更为必填</td><td>倪文斌</td></tr><tr><td>2025/03/19</td><td>2.0</td><td>增加获取公司 api;车辆详情返回车尺寸</td><td>肖伟</td></tr><tr><td>2025/03/24</td><td>2.1</td><td>任务 id 类型变成 long</td><td>范亚运</td></tr><tr><td>2025/03/23</td><td>2.2</td><td>增加停靠点相关 api;增加到达指令</td><td>肖伟</td></tr><tr><td>2025/04/11</td><td>2.3</td><td>增加配置、解除车辆和停靠点关联关系相关 api</td><td>倪文斌</td></tr><tr><td>2025/06/25</td><td>2.4</td><td>新增一键靠边、重新起步指令</td><td>肖伟</td></tr><tr><td>2025/08/06</td><td>2.5</td><td>更新 url</td><td>肖伟</td></tr></table>


让物流更简单


<table><tr><td>2025/09/09</td><td>2.6</td><td>车辆 api 新增冷机、格口数、城市编码;停靠点列表增加省市返回</td><td>肖伟</td></tr><tr><td>2025/10/30</td><td>2.7</td><td>获取所有停靠点的 api 增加经停点查询非必填参数;新增停靠点新增经停点适配;增加经停点配置和查询 api</td><td>肖伟</td></tr><tr><td>2025/11/20</td><td>2.8</td><td>停靠点列表查询增加省市编码返回;经停点查询增加 gcj02 坐标返回</td><td>肖伟</td></tr><tr><td>2026/02/09</td><td>2.9</td><td>跑圈接口补充非必填参数:出发点;审计整改,去除部分字段</td><td>李鹏飞</td></tr></table>

目录
特别声明....5
产品说明....5
使用流程....5
全局参数....5
九识提供....6
返回值说明....6
1. 认证 Token 获取....6
2. 获取所有公司列表....8
3. 获取所有站点列表....11
4. 获取所有停靠点列表....14
5. 获取所有车辆列表....18
6. 获取车辆详情....23
7. 添加任务并出发....28
8. 添加装单任务并出发....30
9. 添加跑圈任务....34
10. 取消任务....37
11. 车辆出发....38

12. 开格口....39
13. 语音播报和点阵屏显示....41
14. 常用指令接口....42
15. 查询当前车辆规划路径....43
16. 预览车辆规划路径....46
17. 停靠点....49
17.1 新增停靠点....49
17.2 修改停靠点....51
17.3 删除停靠点....53
18. 配置车辆和停靠点的关联关系....54
19. 解除车辆和停靠点的关联关系....55
环境资料....61
1. 认证用的 appid....61
2. 服务器地址....61
附录....61
1. 车辆业务状态列表(vehicleBusinessStatus)....61
2. 常用指令类型....62

## 特别声明

未得到本公司的书面许可，不得为任何目的、以任何形式或手段（包括但不限于机械的或电子的）复制或传播本文档的任何部分。对于本文档涉及的技术和产品，本公司拥有其专利（或正在申请专利）、商标、版权或其它知识产权。除非得到本公司的书面许可协议，本文档不授予这些专利、商标、版权或其它知识产权的许可。

本文档因产品功能示例和描述的需要，所使用的任何人名、企业名和数据都是虚构的，并仅限于本公司内部测试使用，不等于本公司有对任何第三方的承诺和宣传。

## 产品说明

本开发手册对该系统功能接口进行详细的描述, 通过该指南可以对本系统有全面的了解, 使技术人员尽快掌握本系统的接口, 并能够在本系统上进行开发。

## 使用流程

1. 准备阶段:

A. 申请 appid 等信息;

B. 取得开发手册（本文档）等资料；

2. 开发阶段:

A. 根据开发文档快速熟悉对接接口;

B. 根据本系统提供的接口，在自己的系统上进行开发，实现所需要的业务功能；

C. 对自己系统的业务功能进行全面测试;

D. 如无特殊情况和说明，客户可以直接用生产环境进行调试。因为测试环境没有客户的车辆。车上有一个红色的急停按钮，拍下后，车辆不会移动。

## 全局参数

描述：除了 token 获取的接口, 其他接口的请求, 必须携带 token 的 header 用于校验身份信息, token 来自于下面的 app 认证 token 获取接口

请求头参数说明：

<table><tr><td>参数名</td><td>参数值</td><td>参数描述</td></tr></table>

```txt
"success": true,  
"errorCode": null,  
"message": null,  
"data": {具体实例}
```


让物流更简单


<table><tr><td>token</td><td>{{token_var}}</td><td>接口必须的 token,需调用认证接口获取</td></tr></table>

## 九识提供

返回值说明

返回示例：(200) 成功

返回示例：(200)失败

```txt
"success": false,  
"errorCode": "COM0000",  
"message": {错误原因},  
"data": null
```

## 1. 认证 Token 获取

测试环境 URL: http://auth-uat.zelostech.com.cn/app/accessToken

正式环境 URL: https://auth.zelostech.com.cn/app/accessToken

Content-Type: application/json 

请求方式: post

接口说明：该接口返回的 token 是其他 请求的基础。其他所有接口的 header 中需要包含名为 token 的参数。

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr></table>


让物流更简单


<table><tr><td>appId</td><td></td><td>String</td><td>是</td><td>应用 id</td></tr><tr><td>appKey</td><td></td><td>String</td><td>是</td><td>应用密钥</td></tr></table>

请求示例:

<table><tr><td>{</td></tr><tr><td>&quot;appId&quot;: &quot;xxxx&quot;,</td></tr><tr><td>&quot;appKey&quot;: &quot;xxxxx&quot;</td></tr><tr><td>}</td></tr></table>


返回参数说明：


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功响应</td></tr><tr><td>errorCode</td><td>null</td><td>String</td><td>暂无描述</td></tr><tr><td>message</td><td>null</td><td>String</td><td>暂无描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>返回数据</td></tr><tr><td>data.token</td><td>eyJOeXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9. eyJzdWIiOiJhdXRoLXN1cnZlciIsImlhdCI6MTY3NDEwNTk3NjkzMSwiZXhwIjoxNjcOMTkyMzc2OTMxLCJqdGkiOiJmYWE2ND1hNS1iY2N1LTQ4YTEtOWF1MC00ZTZiZmJjNTE3NzQiLCJ1c2VySWQiOm51bGwsIm9yZ2FuaXphdGlvbklkIjpudWxsLCJ2ZWhpY2x1SWQiOm51bGwsImFwcElkJoidG9vbDEwMDEiLCJhdXRob3JpdGllcyI6bnVsbH0.A3QKrGgdGFZcSZjGZ2Cr1ZS0LJGbTuMRBQ3BW6qv7FU</td><td>String</td><td>token 值,用于请求需要认证的接口</td></tr><tr><td>data.expiresAfter</td><td>1440</td><td>Integer</td><td>表示 token 在多少分钟后过期</td></tr></table>


让物流更简单


<table><tr><td>data.id</td><td>6</td><td>Integer</td><td>app 的主键</td></tr></table>

返回示例:

```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "token":
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWliOiJhdXRoLXNlcnZlciIsImlhdCI6MTY3NDEwNTk3NjkzMSwiZXhwljoxNjc0MTkyMzc2OTMxLCJqdGkiOiJmYWE2NDlhNS1iY2NILTQ4YTEtOWFIMC00ZTZiZmJjNTE3NzQiLCJ1c2VySWQiOm51bGwsIm9yZ2FuaXphdGlvbklkljpudWxsLCJ2ZWhpY2xlSWQiOm51bGwsImFwcElkljoidG9vbDEwMDEiLCJhdXRob3JpdGllyl6bnVsbH0.A3QKrGgdGFZcSZjGZ2Cr1ZS0LJGbTuMRBQ3BW6qv7FU",
    "expiresAfter": 1440,
    "id": 6
} 
```

## 2. 获取所有公司列表

```txt
测试环境 URL:
http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/organizations?pageNumber=1&pageSize=20
正式环境 URL:
https://gateway.zelostech.com.cn/business-proxy/open-apis/organizations?pageNumber=1&pageSize=20
```

请求方式：get

接口说明：查询公司列表。如果是代理商公司，还会返回旗下所有的子公司列表。

URI 参数说明:

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>pageNumber</td><td>1</td><td>String</td><td>是</td><td>页号,从1开始</td></tr><tr><td>pageSize</td><td>20</td><td>String</td><td>是</td><td>每页记录数</td></tr></table>


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>接口成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>String</td><td>接口失败时,错误码</td></tr><tr><td>message</td><td>null</td><td>String</td><td>接口失败时,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.total</td><td>48</td><td>Integer</td><td>总记录数</td></tr><tr><td>data.list</td><td></td><td>Array</td><td>记录集合对象</td></tr><tr><td>data.list.id</td><td>626</td><td>Integer</td><td>公司id</td></tr><tr><td>data.list.name</td><td>A有限公司</td><td>String</td><td>公司名称</td></tr><tr><td>data.pageNum</td><td>1</td><td>Integer</td><td>页号,从1开始</td></tr><tr><td>data.pageSize</td><td>20</td><td>Integer</td><td>每页记录数</td></tr><tr><td>data.size</td><td>20</td><td>Integer</td><td>当前页记录数</td></tr><tr><td>data.startRow</td><td>1</td><td>Integer</td><td>开始行,从1开始</td></tr><tr><td>data.endRow</td><td>20</td><td>Integer</td><td>结束行</td></tr><tr><td>data.pages</td><td>3</td><td>Integer</td><td>页数</td></tr><tr><td>data.prePage</td><td>0</td><td>Integer</td><td>上一页</td></tr><tr><td>data.nextPage</td><td>2</td><td>Integer</td><td>下一页</td></tr><tr><td>data.isFirstPage</td><td>true</td><td>Boolean</td><td>是否第一页</td></tr><tr><td>data.isLastPage</td><td>false</td><td>Boolean</td><td>是否最后一页</td></tr><tr><td>data.hasPreviousPage</td><td>false</td><td>Boolean</td><td>是否有上一页</td></tr><tr><td>data.hasNextPage</td><td>true</td><td>Boolean</td><td>是否有下一页</td></tr><tr><td>data.navigatePages</td><td>8</td><td>Integer</td><td>暂无描述</td></tr><tr><td>data.navigatepageN</td><td>1</td><td>Array</td><td>暂无描述</td></tr></table>


让物流更简单


<table><tr><td>ums</td><td></td><td></td><td></td></tr><tr><td>data.navigateFirstPage</td><td>1</td><td>Integer</td><td>暂无描述</td></tr><tr><td>data.navigateLastPage</td><td>3</td><td>Integer</td><td>暂无描述</td></tr></table>

返回示例：(200) 成功

```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "total": 48,
    "list": [
    {
    "id": 626,
    "name": "A 公司"
    }
    ],
    "pageNum": 1,
    "pageSize": 20,
    "size": 20,
    "startRow": 1,
    "endRow": 20,
    "pages": 3,
    "prePage": 0,
    "nextPage": 2,
    "isFirstPage": true,
    "isLastPage": false,
    "hasPreviousPage": false,
    "hasNextPage": true,
    "navigatePages": 8,
    "navigatepageNums": [
```


让物流更简单


```json
1
],
"navigateFirstPage": 1,
"navigateLastPage": 3
}
} 
```

## 3. 获取所有站点列表

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/stations?pageNumber=1&pageSize=20&organizationId=114 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/stations?pageNumber=1&pageSize=20&organizationId=114 

请求方式：get

接口说明：查询公司下面所有的站点列表。能查询本公司或者代理商旗下某公司的站点列表。

URI 参数说明:

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>pageNumber</td><td>1</td><td>String</td><td>是</td><td>页号,从1开始</td></tr><tr><td>pageSize</td><td>20</td><td>String</td><td>是</td><td>每页记录数</td></tr><tr><td>organizationId</td><td>104</td><td>Integer</td><td>否</td><td>非必填。用于查询代理商下级公司下面的站点。这个公司需要属于当前appId的所属公司。</td></tr></table>

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>接口成功与否</td></tr></table>


让物流更简单


<table><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败时,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败时,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.total</td><td>48</td><td>Integer</td><td>总记录数</td></tr><tr><td>data.list</td><td></td><td>Array</td><td>记录集合对象</td></tr><tr><td>data.list.id</td><td>626</td><td>Integer</td><td>站点id</td></tr><tr><td>data.list.name</td><td>同济大学(嘉定校区)测试场</td><td>String</td><td>站点名称</td></tr><tr><td>data.list.gcj02Lon</td><td>121.20965801031939</td><td>Number</td><td>经度gcj02</td></tr><tr><td>data.list.gcj02Lat</td><td>31.289126007319737</td><td>Number</td><td>纬度gcj02</td></tr><tr><td>data.pageNum</td><td>1</td><td>Integer</td><td>页号,从1开始</td></tr><tr><td>data.pageSize</td><td>20</td><td>Integer</td><td>每页记录数</td></tr><tr><td>data.size</td><td>20</td><td>Integer</td><td>当前页记录数</td></tr><tr><td>data.startRow</td><td>1</td><td>Integer</td><td>开始行,从1开始</td></tr><tr><td>data.endRow</td><td>20</td><td>Integer</td><td>结束行</td></tr><tr><td>data.pages</td><td>3</td><td>Integer</td><td>页数</td></tr><tr><td>data.prePage</td><td>0</td><td>Integer</td><td>上一页</td></tr><tr><td>data.nextPage</td><td>2</td><td>Integer</td><td>下一页</td></tr><tr><td>data.isFirstPage</td><td>true</td><td>Boolean</td><td>是否第一页</td></tr><tr><td>data.isLastPage</td><td>false</td><td>Boolean</td><td>是否最后一页</td></tr><tr><td>data.hasPreviousPage</td><td>false</td><td>Boolean</td><td>是否有上一页</td></tr><tr><td>data.hasNextPage</td><td>true</td><td>Boolean</td><td>是否有下一页</td></tr><tr><td>data.navigatePages</td><td>8</td><td>Integer</td><td>暂无描述</td></tr><tr><td>data.navigatepageNums</td><td>1</td><td>Array</td><td>暂无描述</td></tr><tr><td>data.navigateFirst</td><td>1</td><td>Integer</td><td>暂无描述</td></tr></table>


让物流更简单


<table><tr><td>Page</td><td></td><td></td><td></td></tr><tr><td>data.navigateLastPage</td><td>3</td><td>Integer</td><td>暂无描述</td></tr></table>

返回示例：(200) 成功

```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "total": 48,
    "list": [
    {
    "id": 626,
    "name": "同济大学（嘉定校区）测试场",
    "gcj02Lon": 121.20965801031939,
    "gcj02Lat": 31.289126007319737
    }
    ],
    "pageNum": 1,
    "pageSize": 20,
    "size": 20,
    "startRow": 1,
    "endRow": 20,
    "pages": 3,
    "prePage": 0,
    "nextPage": 2,
    "isFirstPage": true,
    "isLastPage": false,
    "hasPreviousPage": false,
    "hasNextPage": true,
    "navigatePages": 8,
    "navigatepageNums": [
```

```json
1
],
"navigateFirstPage": 1,
"navigateLastPage": 3
}
} 
```

## 4. 获取所有停靠点列表

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/stops?pageNumber=1&pageSize=20&stationId=59 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/stops?pageNumber=1&pageSize=20&stationId=59 

请求方式：get

接口说明：查询公司下面所有的停靠点列表，可按站点过滤


URI 参数说明:


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>pageNumber</td><td>1</td><td>String</td><td>是</td><td>页号,从1开始</td></tr><tr><td>pageSize</td><td>20</td><td>String</td><td>是</td><td>每页记录数</td></tr><tr><td>stationId</td><td>59</td><td>String</td><td>否</td><td>站点id。非必填。为空则查询全部站点的全部停靠点</td></tr><tr><td>includingPassBy</td><td>true</td><td>Boolean</td><td>否</td><td>是否查询经停点。如果不需要指定任务路线,这个字段可以不用传。</td></tr></table>

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr></table>


让物流更简单


<table><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.total</td><td>579</td><td>Integer</td><td>总数目</td></tr><tr><td>data.list</td><td></td><td>Array</td><td>记录集合</td></tr><tr><td>data.list.id</td><td>11</td><td>Integer</td><td>停靠点id</td></tr><tr><td>data.list.name</td><td>莲花新村</td><td>String</td><td>停靠点名称</td></tr><tr><td>data.list.type</td><td>HOME</td><td>String</td><td>停靠点类型。HOME:home点。通常表示车辆站点的起始位置,暂时无特别作用。可以用于发任务,车辆会在这类点上停车。和PARKING点相同。PARKING:取货点,停靠点。可以用于发送任务点位,车辆能在这类点位停车。PASS_BY:经停点。可用于规定任务路径。车辆能在前往PARKING点过程中经过这类点位,但是不会在这类点位停车。</td></tr><tr><td>data.list.stationId</td><td>2</td><td>Integer</td><td>站点id</td></tr><tr><td>data.list.stationN</td><td>苏州-TEST</td><td>String</td><td>站点名称</td></tr></table>


让物流更简单


<table><tr><td>ame</td><td></td><td></td><td></td></tr><tr><td>data.list.heading</td><td>1.222</td><td>Double</td><td>朝向,弧度制。正东为0,逆时针为正,顺时针为付。例:1.57为正北,3.14为正西,-1.57为正南,-3.14为正西</td></tr><tr><td>data.list.gcj02Lon</td><td>120.7444254992492</td><td>Double</td><td>经度gcj02</td></tr><tr><td>data.list.gcj02Lat</td><td>31.286320769966363</td><td>Double</td><td>纬度gcj02</td></tr><tr><td>data.list.cityId</td><td>7</td><td>Integer</td><td>停靠点所属城市id</td></tr><tr><td>data.list.cityName</td><td>苏州市</td><td>String</td><td>停靠点所属城市名</td></tr><tr><td>data.list.cityCode</td><td>410000</td><td>String</td><td>城市6位行政编码。需要从九识获取完整城市行政编码列表进行比对。</td></tr><tr><td>data.list.stateId</td><td>2</td><td>Integer</td><td>停靠点所属省份id</td></tr><tr><td>data.list.stateName</td><td>江苏省</td><td>String</td><td>停靠点所属省份名</td></tr><tr><td>data.list.stateCode</td><td>400000</td><td>String</td><td>省份6位行政编码。需要从九识获取完整城市行政编码列表进行比对。</td></tr><tr><td>data.pageNum</td><td>1</td><td>Integer</td><td>页号,从1开始</td></tr><tr><td>data.pageSize</td><td>20</td><td>Integer</td><td>每页记录数</td></tr><tr><td>data.size</td><td>20</td><td>Integer</td><td>当前页记录数</td></tr><tr><td>data.startRow</td><td>1</td><td>Integer</td><td>开始行,从1开始</td></tr><tr><td>data.endRow</td><td>20</td><td>Integer</td><td>结束行</td></tr><tr><td>data.pages</td><td>29</td><td>Integer</td><td>页数</td></tr><tr><td>data.prePage</td><td>0</td><td>Integer</td><td>上一页</td></tr></table>


让物流更简单


<table><tr><td>data.nextPage</td><td>2</td><td>Integer</td><td>下一页</td></tr><tr><td>data.isFirstPage</td><td>true</td><td>Boolean</td><td>是否第一页</td></tr><tr><td>data.isLastPage</td><td>false</td><td>Boolean</td><td>是否最后一页</td></tr><tr><td>data.hasPreviousPage</td><td>false</td><td>Boolean</td><td>是否有上一页</td></tr><tr><td>data.hasNextPage</td><td>true</td><td>Boolean</td><td>是否有下一页</td></tr><tr><td>data.navigatePages</td><td>8</td><td>Integer</td><td>暂无描述</td></tr><tr><td>data.navigatepageNums</td><td>1</td><td>Array</td><td>暂无描述</td></tr><tr><td>data.navigateFirstPage</td><td>1</td><td>Integer</td><td>暂无描述</td></tr><tr><td>data.navigateLastPage</td><td>8</td><td>Integer</td><td>暂无描述</td></tr></table>


返回示例：(200) 成功


```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "total": 579,
    "list": [
    {
    "id": 11,
    "name": "莲花新村",
    "stationId": 2,
    "stationName": "苏州-TEST",
    "gcj02Lon": 120.7444254992492,
    "gcj02Lat": 31.286320769966363,
    "cityId": 7,
    "cityName": "Carla"
    }
    ]
}
```


让物流更简单


```txt
"cityCode": "909000",
"stateId": 2,
"stateName": "江苏省",
"stateCode": "320000"
}
],
"pageNum": 1,
"pageSize": 20,
)size": 20,
"startRow": 1,
"endRow": 20,
"pages": 29,
"prePage": 0,
"nextPage": 2,
"isFirstPage": true,
"isLastPage": false,
"hasPreviousPage": false,
"hasNextPage": true,
"navigatePages": 8,
"navigatepageNums": [
    1
],
"navigateFirstPage": 1,
"navigateLastPage": 8
}
```

## 5. 获取所有车辆列表

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicles? 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicles?pageNumber=1&pageSize=20&stationId=4 

请求方式：get

接口说明：获取公司下面所有车辆列表，可按站点过滤。


URI 参数说明:


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>pageNumber</td><td>1</td><td>String</td><td>是</td><td>页号,从1开始</td></tr><tr><td>pageSize</td><td>20</td><td>String</td><td>是</td><td>每页记录数</td></tr><tr><td>stationId</td><td>4</td><td>String</td><td>否</td><td>站点id</td></tr></table>


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.total</td><td>8</td><td>Integer</td><td>总数目</td></tr><tr><td>data.list</td><td></td><td>Array</td><td>记录集合对象</td></tr><tr><td>data.list.id</td><td>83</td><td>Integer</td><td>车辆 id</td></tr><tr><td>data.list.name</td><td>SZ00001</td><td>String</td><td>车辆名称</td></tr><tr><td>data.list.number</td><td>苏 SZ0001</td><td>String</td><td>车辆牌照</td></tr><tr><td>data.list.vin</td><td>123456789012345</td><td>String</td><td>车架号</td></tr><tr><td>data.list.stationId</td><td>4</td><td>Integer</td><td>站点 id</td></tr><tr><td>data.list.stationName</td><td>回收站</td><td>String</td><td>站点名称</td></tr><tr><td>data.list.businessStatus</td><td>IDLE</td><td>String</td><td>业务状态</td></tr></table>


让物流更简单


<table><tr><td>data.list.business StatusName</td><td>空闲</td><td>String</td><td>业务状态名称</td></tr><tr><td>data.list.model</td><td>2023-Z5</td><td>String</td><td>车型名称</td></tr><tr><td>data.list.boxVolume</td><td>1</td><td>Double</td><td>货箱容积</td></tr><tr><td>data.list.boxSize</td><td>3752.0*1716.0*1890.0</td><td>String</td><td>每个箱体长*宽*高mm</td></tr><tr><td>data.list.weight</td><td>800</td><td>Double</td><td></td></tr><tr><td>data.list.vehicleSize</td><td>6666*2222*7777</td><td>String</td><td>车长*宽*高。单位mm</td></tr><tr><td>data.list.noLoadEndurance</td><td>100</td><td>Double</td><td>空载续航</td></tr><tr><td>data.list.fullLoadEndurance</td><td>80</td><td>Double</td><td>满载续航</td></tr><tr><td>data.list.maxSpeed</td><td>6.94</td><td>Double</td><td>最大速度</td></tr><tr><td>data.list.cityCode</td><td>330100</td><td>String</td><td>城市编码</td></tr><tr><td>data.list.cityName</td><td>苏州市</td><td>String</td><td>城市名称</td></tr><tr><td>data.list.gridNo</td><td>1</td><td>Integer</td><td>车辆的格口数量</td></tr><tr><td>data.list.chillerControl</td><td>true</td><td>Boolean</td><td>车辆是否有冷机,即是否能制冷</td></tr><tr><td>data.pageNum</td><td>1</td><td>Integer</td><td>页号,从1开始</td></tr><tr><td>data.pageSize</td><td>20</td><td>Integer</td><td>每页条目</td></tr><tr><td>data.size</td><td>8</td><td>Integer</td><td>当前页记录数</td></tr><tr><td>data.startRow</td><td>1</td><td>Integer</td><td>开始行,从1开始</td></tr><tr><td>data.endRow</td><td>8</td><td>Integer</td><td>结束行</td></tr><tr><td>data.pages</td><td>1</td><td>Integer</td><td>页数</td></tr><tr><td>data.prePage</td><td>0</td><td>Integer</td><td>上一页</td></tr><tr><td>data.nextPage</td><td>0</td><td>Integer</td><td>下一页</td></tr></table>


让物流更简单


<table><tr><td>data.isFirstPage</td><td>true</td><td>Boolean</td><td>是否第一页</td></tr><tr><td>data.isLastPage</td><td>true</td><td>Boolean</td><td>是否最后一页</td></tr><tr><td>data.hasPreviousPage</td><td>false</td><td>Boolean</td><td>是否有上一页</td></tr><tr><td>data.hasNextPage</td><td>false</td><td>Boolean</td><td>是否有下一页</td></tr><tr><td>data.navigatePages</td><td>8</td><td>Integer</td><td>暂无描述</td></tr><tr><td>data.navigatepageNums</td><td>1</td><td>Array</td><td>暂无描述</td></tr><tr><td>data.navigateFirstPage</td><td>1</td><td>Integer</td><td>暂无描述</td></tr><tr><td>data.navigateLastPage</td><td>1</td><td>Integer</td><td>暂无描述</td></tr></table>


返回示例：(200) 成功


```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "total": 8,
    "list": [
    {
    "id": 83,
    "name": "SZ00001",
    "number": "苏 SZ0001",
    "vin": "1234567890123",
    "stationId": 4,
    "stationName": "回收站",
    "businessStatus": "IDLE",
    "businessStatusName": "空闲",
    "model": "Z2-2024"
    }
    ]
}
```


让物流更简单


```txt
"boxVolume": 1,
"boxSize": "1*2*3",
"weight": 800,
"vehicleSize": "4030.0*1716.0*2927.0",
"noLoadEndurance": 1,
"fullLoadEndurance": 1,
"maxSpeed": 1,
"cityCode": "330100",
"cityName": "杭州市",
"gridNo": 1,
"chillerControl": true
}
],
"pageNum": 1,
"pageSize": 20,
)size": 8,
"startRow": 1,
"endRow": 8,
"pages": 1,
"prePage": 0,
"nextPage": 0,
"isFirstPage": true,
"isLastPage": true,
"hasPreviousPage": false,
"hasNextPage": false,
"navigatePages": 8,
"navigatepageNums": [
    1
],
"navigateFirstPage": 1,
"navigateLastPage": 1
}
```

## 6. 获取车辆详情

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle?vehicleName=ZL00106 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle?vehicleName=ZL00106 

请求方式：get

接口说明：获取车辆的详情，包含车辆绑定列表，格口，业务状态等。同一辆车1s只能查询一次。


URI 参数说明:


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr></table>


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.id</td><td>150</td><td>Integer</td><td>车辆 id</td></tr><tr><td>data.name</td><td>ZL00106</td><td>String</td><td>车辆名称</td></tr><tr><td>data.number</td><td>苏 ZL0106</td><td>String</td><td>车辆牌照</td></tr><tr><td>data.vin</td><td>1234567890123</td><td>String</td><td>车架号</td></tr><tr><td>data.stationId</td><td>111</td><td>Integer</td><td>站点 id</td></tr><tr><td>data.stationName</td><td>九识车辆维修保养中心</td><td>String</td><td>站点名称</td></tr></table>


让物流更简单


<table><tr><td>data.gridNos</td><td>1</td><td>Array</td><td>格口号列表</td></tr><tr><td>data.stops</td><td></td><td>Array</td><td>车辆已绑定的停靠点列表</td></tr><tr><td>data.stops.id</td><td>2849</td><td>Integer</td><td>停靠点 id</td></tr><tr><td>data.stops.name</td><td>利物浦大学</td><td>String</td><td>停靠点名称</td></tr><tr><td>data.stops.gcj02Lon</td><td>120.7386512925433</td><td>Double</td><td>停靠点经度 gcj02</td></tr><tr><td>data.stops.gcj02Lat</td><td>31.27170288271641</td><td>Double</td><td>停靠点纬度 gcj02</td></tr><tr><td>data.gcj02Lon</td><td>120.81111654091693</td><td>Double</td><td>车辆经度 gcj02</td></tr><tr><td>data.gcj02Lat</td><td>31.328151778765793</td><td>Double</td><td>车辆纬度 gcj02</td></tr><tr><td>data.online</td><td>true</td><td>Boolean</td><td>车辆是否在线</td></tr><tr><td>data.batteryCharging</td><td>false</td><td>Boolean</td><td>车辆是否充电中</td></tr><tr><td>data.batteryPower</td><td>56.25</td><td>Number</td><td>车辆电池百分比,值56表示电量56%</td></tr><tr><td>data.businessStatus</td><td>ATSTOP</td><td>String</td><td>车辆业务状态</td></tr><tr><td>data.businessStatusName</td><td>投递</td><td>String</td><td>车辆业务状态名称</td></tr><tr><td>data.dispatchId</td><td>36</td><td>Long</td><td>车辆任务 id</td></tr><tr><td>data.currentGoalIndex</td><td>1</td><td>Integer</td><td>车辆任务当前目的地Index,0表示还未触发,1表示去往第一个停靠点或者已经到第一个达停靠点,可结合车辆业务状态和goals列表判断</td></tr><tr><td>data.goals</td><td></td><td>Array</td><td>车辆目的地列表</td></tr></table>


让物流更简单


<table><tr><td>data.goals.stopId</td><td>2852</td><td>Integer</td><td>目的地停靠点 id</td></tr><tr><td>data.goals.stopName</td><td>QA_home 点_辅路</td><td>String</td><td>目的地停靠点名称</td></tr><tr><td>data.goals.goalIndex</td><td>0</td><td>Integer</td><td>目的地停靠点 index,注意,如果为 0,则表示该停靠点是起点,去往的停靠点是从 1 开始</td></tr><tr><td>data.goals.arrivalDatetime</td><td>2024-07-01 14:10:46</td><td>Date</td><td>目的地停靠点到达时间</td></tr><tr><td>data.goals.departureDatetime</td><td>2024-07-01 14:16:46</td><td>Date</td><td>目的地停靠点离开时间</td></tr><tr><td>data.model</td><td>2023-Z5</td><td>String</td><td>车型名称</td></tr><tr><td>data.boxVolume</td><td>1</td><td>Double</td><td>货箱容积</td></tr><tr><td>data.boxSize</td><td>3752.0*1716.0*1890.0</td><td>String</td><td>每个箱体长*宽*高mm</td></tr><tr><td>data.weight</td><td>800</td><td>Double</td><td></td></tr><tr><td>data.vehicleSize</td><td>6666*2222*7777</td><td>String</td><td>车长*宽*高。单位 mm</td></tr><tr><td>data.noLoadEndurance</td><td>100</td><td>Double</td><td>空载续航</td></tr><tr><td>data.fullLoadEndurance</td><td>80</td><td>Double</td><td>满载续航</td></tr><tr><td>data.maxSpeed</td><td>6.94</td><td>Double</td><td>最大速度</td></tr><tr><td>data.list.cityCode</td><td>330100</td><td>String</td><td>城市编码</td></tr><tr><td>data.list.cityName</td><td>苏州市</td><td>String</td><td>城市名称</td></tr><tr><td>data.list.chillerControl</td><td>true</td><td>Boolean</td><td>车辆是否有冷机,即是否能制冷</td></tr></table>


返回示例：(200) 成功


```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "id": 150,
    "name": "ZL00106",
    "number": "苏 ZL0106",
    "stationId": 111,
    "stationName": "九识车辆维修保养中心",
    "gridNos": [
    1,
    2,
    3,
    4
    ],
    "stops": [
    {
    "id": 2849,
    "name": "利物浦大学",
    "gcj02Lon": 120.7386512925433,
    "gcj02Lat": 31.27170288271641
    }
    ],
    "gcj02Lon": 120.81111654091693,
    "gcj02Lat": 31.328151778765793,
    "online": true,
    "batteryCharging": false,
    "batteryPower": 55,
    "businessStatus": "ONWAY",
    "businessStatusName": "去程",
    "dispatchId": 43,
    "currentGoalIndex": 1,
    "goals": [
```


让物流更简单



让物流更简单


```json
{
    "stopId": 2852,
    "stopName": "QA_home 点_辅路",
    "goalIndex": 0,
    "arrivalDatetime": null,
    "departureDatetime": "2024-07-01 16:10:31"
},
{
    "stopId": 2891,
    "stopName": "停靠点 1",
    "goalIndex": 1,
    "arrivalDatetime": null,
    "departureDatetime": null
},
{
    "stopId": 2892,
    "stopName": "停靠点 2",
    "goalIndex": 2,
    "arrivalDatetime": null,
    "departureDatetime": null
}
],
"model": "Z2-2024",
"boxVolume": 1,
"boxSize": "null*null*null",
"weight": 800,
"vehicleSize": "4030.0*1716.0*2927.0",
"noLoadEndurance": 1,
"fullLoadEndurance": 1,
"maxSpeed": 1,
"cityCode": "330100",
"cityName": "杭州市",
"chillerControl": true
}
```

## 7. 添加任务并出发

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/add_dispatch 

正式环境 URL:

http://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/add_dispatch 

Content-Type: application/json 

请求方式：post

接口说明：给车辆添加一个普通的任务，并在规划结束后自动出发。

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>fromStopId</td><td>2852</td><td>Integer</td><td>否</td><td>出发停靠点 id</td></tr><tr><td>toStopIds</td><td>2891</td><td>Array</td><td>是</td><td>去往目的地停靠点列表</td></tr><tr><td>speedLimit</td><td>5</td><td>Number</td><td>否</td><td>车辆限速,单位 m/s</td></tr></table>

请求示例:

```json
{
    "vehicleName": "ZL00106",
    "fromStopId": 2852,
    "toStopIds": [
    2891, 2892
],
    "speedLimit": 4
} 
```

## 让物流更简单


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.vehicleId</td><td>150</td><td>Integer</td><td>车辆 id</td></tr><tr><td>data.vehicleName</td><td>ZL00106</td><td>String</td><td>车辆名称</td></tr><tr><td>data.vehicleNumber</td><td>苏 ZL0106</td><td>String</td><td>车辆牌照</td></tr><tr><td>data.vehicleBusinessStatus</td><td>ROUTING</td><td>String</td><td>车辆业务状态。附录 1</td></tr><tr><td>data.vehicleBusinessStatusName</td><td>规划中</td><td>String</td><td>车辆业务状态名称。附录 1</td></tr><tr><td>data.dispatchId</td><td>43</td><td>Long</td><td>车辆任务 id</td></tr></table>


返回示例：(200) 成功


```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "vehicleId": 150,
    "vehicleName": "ZL00106",
    "vehicleNumber": "苏 ZL0106",
    "vehicleBusinessStatus": "ROUTING",
    "vehicleBusinessStatusName": "规划中",
    "dispatchId": 43
    }
}
```


让物流更简单


<table><tr><td>常见失败响应 message</td></tr><tr><td>车辆不存在!</td></tr><tr><td>车辆已有任务!</td></tr><tr><td>起始停靠点(id=%s)未找到!</td></tr><tr><td>车辆自动驾驶中,无法操作</td></tr><tr><td>停靠点不存在</td></tr><tr><td>停靠点未绑定:%s</td></tr></table>

## 8. 添加装单任务并出发

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/add_dispatch_order_and_go 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/add_dispatch_order_and_go 

Content-Type: application/json 

请求方式：post

接口说明：给车辆添加一个带有订单数据任务并在规划结束后自动出发。车辆到达停靠点会给 order 中的 contact 发取货短信/通知取货电话（根据车辆站点的通知配置通知）


请求体参数说明：


<table><tr><td>参数名</td><td>参数值</td><td>是否必填</td><td>参数类型</td><td>描述说明</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>是</td><td>String</td><td>车辆名称</td></tr><tr><td>fromStopId</td><td>79</td><td>否</td><td>Integer</td><td>出发停靠点 id</td></tr><tr><td>toStops</td><td></td><td>是</td><td>Array</td><td>去往目的地停靠点列表</td></tr><tr><td>toStops.stopId</td><td>61</td><td>是</td><td>Number</td><td>停靠点 id</td></tr><tr><td>toStops.loadContac</td><td>17751163739</td><td>否</td><td>String</td><td>装货人手机号</td></tr></table>


让物流更简单


<table><tr><td>t</td><td></td><td></td><td></td><td></td></tr><tr><td>toStops.loadUserName</td><td>装货人</td><td>否</td><td>String</td><td>装货人姓名</td></tr><tr><td>toStops.orders</td><td></td><td>否</td><td>Array</td><td>订单列表</td></tr><tr><td>toStops.orders.gridNos</td><td></td><td>否</td><td>Array</td><td>格口列表</td></tr><tr><td>toStops.orders.contacts</td><td></td><td>否</td><td>Array</td><td>联系人列表</td></tr><tr><td>toStops.orders.contacts.contact</td><td>13905387660</td><td>否</td><td>String</td><td>联系人手机号</td></tr><tr><td>toStops.orders.contacts.userName</td><td>取货人4</td><td>否</td><td>String</td><td>联系人姓名</td></tr><tr><td>speedLimit</td><td>5</td><td>Number</td><td>否</td><td>车辆限速,单位m/s</td></tr></table>

## 请求示例:

```json
{
    "vehicleName": "ZL00106",
    "fromStopId": 2852,
    "speedLimit": 4,
    "toStops": [
    {
    "stopId": 2891,
    "loadContact": "17751163739",
    "loadUserName": "装货人",
    "orders": [
    {
    "gridNos": [
    1
    ],
    "contacts": [
    {
```


让物流更简单


![image](https://cdn-mineru.openxlab.org.cn/result/2026-07-07/551e9b05-9197-40ff-bf27-c6352fc1371e/1d7408778cbfde3a028687ebad0aba6d53ef58b2fdc878ab2d38b65405a6ee05.jpg)



让物流更简单


![image](https://cdn-mineru.openxlab.org.cn/result/2026-07-07/551e9b05-9197-40ff-bf27-c6352fc1371e/631027f6873f6352336160d17959f7482d0002a4e1b8cfb680e8c67ba5b1fcfa.jpg)



返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.vehicleId</td><td>150</td><td>Integer</td><td>车辆 id</td></tr><tr><td>data.vehicleName</td><td>ZL00106</td><td>String</td><td>车辆名称</td></tr><tr><td>data.vehicleNumber</td><td>苏 ZL0106</td><td>String</td><td>车辆牌照</td></tr><tr><td>data.vehicleBusinessStatus</td><td>ROUTING</td><td>String</td><td>车辆业务状态。附录 1</td></tr><tr><td>data.vehicleBusinessStatusName</td><td>规划中</td><td>String</td><td>车辆业务状态名称。附录 1</td></tr><tr><td>data.dispatchId</td><td>43</td><td>Long</td><td>车辆任务 id</td></tr></table>

返回示例：(200) 成功

```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "vehicleId": 150,
    "vehicleName": "ZL00106",
    "vehicleNumber": "苏 ZL0106",
    "vehicleBusinessStatus": "ROUTING",
    "vehicleBusinessStatusName": "规划中",
    "dispatchId": 43
    }
}
```

```txt
常见失败响应 message
车辆不存在!
车辆已有任务!
起始停靠点(id=%s)未找到!
车辆自动驾驶中,无法操作
停靠点不存在
停靠点未绑定:%s
```

## 9. 添加跑圈任务

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/add_dispatch_cycle_and_go 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/add 

```txt
dispatch cycle and go 
```

Content-Type: application/json 

请求方式：post

接口说明: 给车辆生成一个能自定义每个点停留时间, 并且不会触发通知逻辑的, 可循环的任务, 并自动出发

## 请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00075</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>fromStopId</td><td>2334</td><td>Number</td><td>否</td><td>出发点</td></tr><tr><td>cycleStops</td><td>-</td><td>Array</td><td>是</td><td>-</td></tr><tr><td>cycleStops.index</td><td>1</td><td>Number</td><td>是</td><td>停靠序号。从1开始</td></tr><tr><td>cycleStops.stopId</td><td>2849</td><td>Number</td><td>是</td><td>停靠的点位id</td></tr><tr><td>cycleStops.waitingTime</td><td>2</td><td>Number</td><td>是</td><td>该点停留时间。单位为分钟</td></tr><tr><td>times</td><td>3</td><td>String</td><td>是</td><td>跑圈次数。即,cycleStops循环次数</td></tr><tr><td>speedLimit</td><td>5</td><td>Number</td><td>否</td><td>车辆限速,单位m/s</td></tr></table>

请求示例:

```json
{
    "vehicleName": "ZL00374",
    "speedLimit": 4,
    "cycleStops": [
    {
    "index": 1,
    "stopId": "2849",
    "waitingTime": 2
    },
} 
```


让物流更简单


```json
{
    "index": 2,
    "stopId": "2852",
    "waitingTime": 0
}
],
"times": "3"
} 
```


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>-</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>-</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>-</td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.vehicleId</td><td>759</td><td>Number</td><td>车辆 id</td></tr><tr><td>data.vehicleName</td><td>ZL00374</td><td>String</td><td>车辆名称</td></tr><tr><td>data.vehicleNumber</td><td>苏 ZL0374</td><td>String</td><td>车辆牌照</td></tr><tr><td>data.vehicleBusinessStatus</td><td>ROUTING</td><td>String</td><td>车辆业务状态。附录 1</td></tr><tr><td>data.vehicleBusinessStatusName</td><td>规划中</td><td>String</td><td>车辆业务状态名称。附录 1</td></tr><tr><td>data.dispatchId</td><td>297066</td><td>Number</td><td>车辆任务 id</td></tr></table>


返回参数说明：(200) 成功


```txt
"success": true, "errorCode": null, 
```

```txt
Content-Type: application/json 
```

```txt
cel dispatch 
```

让物流更简单

```json
"message": null,
"data": {
    "vehicleId": 759,
    "vehicleName": "ZL00374",
    "vehicleNumber": "苏 ZL0374",
    "vehicleBusinessStatus": "ROUTING",
    "vehicleBusinessStatusName": "规划中",
    "dispatchId": 297066
}
```

## 10. 取消任务

测试环境 URL:

```txt
http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/cancel_dispatch 
```

正式环境 URL:

请求方式: post

接口说明：取消车辆任务，车辆会停留在原地。当车辆有速度的时候，不允许取消任务，接口会报错。

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr></table>

请求示例:

```json
{
    "vehicleName": "ZL00106"
} 
```

返回参数说明：(200) 成功


让物流更简单


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>Null</td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

## 11. 车辆出发

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/ 

gO 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/go 

Content-Type: application/json 

请求方式: post

接口说明：车辆在任一停靠点的时候使用。将让车辆出发去往任务的下一个停靠点。

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr></table>

请求示例:

让物流更简单

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>Null</td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

## 12. 开格口

测试环境 URL:

```txt
http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/ 
```

```txt
open box 
```

```txt
正式环境 URL:
```

```txt
https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/ope 
```

```txt
n box 
```

```txt
Content-Type: application/json 
```

请求方式: post

接口说明：打开车辆格口（箱门）

请求体参数说明：


让物流更简单


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>gridNos</td><td>4</td><td>Array</td><td>否</td><td>需要打开的格口号,如果为空,则表示打开所有格口</td></tr></table>

请求示例:


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>Null</td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

## 13. 语音播报和点阵屏显示

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/sound_and_show 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/sound_and_show 

Content-Type: application/json 

请求方式: post

接口说明：车辆语音播报和点阵屏显示

## 请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>sound</td><td>九识智能测试语音</td><td>String</td><td>否</td><td>表示车辆语音播放内容(sound 和 show 字段必须有一个有值)</td></tr><tr><td>show</td><td>九识智能测试显示</td><td>String</td><td>否</td><td>表示车辆点阵屏显示内容(sound 和 show 字段必须有一个有值)</td></tr><tr><td>showDuration</td><td>10</td><td>Number</td><td>否</td><td>点阵屏显示时长,默认 10s,单位秒</td></tr></table>


请求示例:


```json
{
    "vehicleName": "ZL00106",
    "sound": "九识智能测试语音",
    "show": "九识智能测试显示",
    "showDuration": 10
}
```

## 让物流更简单


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td></td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td></td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td></td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

## 14. 常用指令接口

测试环境 URL:

http://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/ 

command 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/com 

mand 

Content-Type: application/json 

请求方式: post

接口说明：下发常用指令

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>commandType</td><td>BUSINESS_SHUTDOWN</td><td>String</td><td>是</td><td>指令类型,具体见附录2</td></tr></table>


让物流更简单


<table><tr><td>userId</td><td>15</td><td>String</td><td>是</td><td>必填。请传当前操作用户系统唯一标识</td></tr><tr><td>userName</td><td>张三</td><td>String</td><td>是</td><td>必填。请传当前操作用户名称</td></tr><tr><td>source</td><td>指令来源的 md5 全小写</td><td>String</td><td>否</td><td>特殊指令必填。具体见附录 2。九识提供常量。传 md5 全小写</td></tr></table>

请求示例:

```json
{
    "vehicleName": "ZL00106",
    "commandType": "BUSINESS_SHUTDOWN"
} 
```

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td></td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td></td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td></td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

```javascript
"success": true, "errorCode": null, "message": null, "data": null 
```

## 15. 查询当前车辆规划路径

测试环境 URL:

https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle 

## 让物流更简单

/current_navigation?vehicleName=ZL00075 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/current_navigation?vehicleName=ZL00075 

请求方式：get

接口说明：查询车辆此刻任务的规划路径,如果车辆此时空闲没有任务，则返回的内容为空。


URI 参数说明:


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00075</td><td>String</td><td>是</td><td>车辆名称</td></tr></table>


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>Null</td><td>接口返回对象</td></tr><tr><td>data.paths</td><td></td><td>Array</td><td>规划的路线数组,每一个都包含了一段路的点坐标集合,有几个停靠点就有几段路</td></tr><tr><td>data.paths.points</td><td></td><td>Array</td><td>一段路的点坐标集合</td></tr><tr><td>data.paths.points.gcj02Lon</td><td></td><td>Number</td><td>点位经度 gcj02</td></tr><tr><td>data.paths.points.gcj02Lat</td><td></td><td>Number</td><td>点位纬度 gcj02</td></tr></table>


返回示例：(200) 成功



让物流更简单


```txt
"success": true,
"errorCode": null,
"message": null,
"data": {
    "paths": [
    {
    "points": [
    {
    "lon": 120.8064011253003,
    "lat": 31.32976731118055,
    "gcj02Lon": 120.81070682285196,
    "gcj02Lat": 31.327724802151984
    },
    {
    "lon": 120.80638060809005,
    "lat": 31.32982309371625,
    "gcj02Lon": 120.81068635056876,
    "gcj02Lat": 31.32778063970071
    },
    {
    "lon": 120.80635150669826,
    "lat": 31.329899904546373,
    "gcj02Lon": 120.81065731269604,
    "gcj02Lat": 31.327857527712254
    },
    {
    "lon": 120.80635150669826,
    "lat": 31.329899904546373,
    "gcj02Lon": 120.81065731269604,
    "gcj02Lat": 31.3278574527712254
    },
    {
    "lon": 120.80627826279631,
    "lat": 31.330094321398644, 
```


让物流更简单


```json
"gc j02Lon": 120.81058422868627,
"gc j02Lat": 31.328052139121546
}
]
}
]
}
} 
```

## 16. 预览车辆规划路径

测试环境 URL:

https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/vehicle/preview_navigation 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicle/preview_navigation 

```txt
Content-Type: application/json 
```

请求方式：post

接口说明：用于预览车辆规划路径，跟车辆的当前位置有关，跟车辆是否有任务无关。

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00106</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>fromStopId</td><td>2852</td><td>Integer</td><td>否</td><td>出发停靠点 id</td></tr><tr><td>toStopIds</td><td>2891</td><td>Array</td><td>是</td><td>去往目的地停靠点列表</td></tr></table>

请求示例:

```txt
"vehicleName": "ZL00075", "fromStopId": 2852, 
```


让物流更简单


![image](https://cdn-mineru.openxlab.org.cn/result/2026-07-07/551e9b05-9197-40ff-bf27-c6352fc1371e/5201119a303abf023221143ee4fdbbda4cbdfd46e5d9df34fac93625982819a3.jpg)



返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td></td><td>接口返回对象</td></tr><tr><td>data.paths</td><td></td><td>Array</td><td>规划的路线数组,每一个都包含了一段路的点坐标集合,有几个停靠点就有几段路</td></tr><tr><td>data.paths.points</td><td></td><td>Array</td><td>一段路的点坐标集合</td></tr><tr><td>data.paths.points.gcj02Lon</td><td></td><td>Number</td><td>点位经度 gcj02</td></tr><tr><td>data.paths.points.gcj02Lat</td><td></td><td>Number</td><td>点位纬度 gcj02</td></tr></table>


返回示例：(200) 成功


```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "paths": [
    {
    "points": [ 
```


让物流更简单


<table><tr><td>{</td></tr><tr><td>&quot;lon&quot;: 120.8064011253003,</td></tr><tr><td>&quot;lat&quot;: 31.32976731118055,</td></tr><tr><td>&quot;gcj02Lon&quot;: 120.81070682285196,</td></tr><tr><td>&quot;gcj02Lat&quot;: 31.327724802151984</td></tr><tr><td>},</td></tr><tr><td>{</td></tr><tr><td>&quot;lon&quot;: 120.80638060809005,</td></tr><tr><td>&quot;lat&quot;: 31.32982309371625,</td></tr><tr><td>&quot;gcj02Lon&quot;: 120.81068635056876,</td></tr><tr><td>&quot;gcj02Lat&quot;: 31.32778063970071</td></tr><tr><td>},</td></tr><tr><td>{</td></tr><tr><td>&quot;lon&quot;: 120.80635150669826,</td></tr><tr><td>&quot;lat&quot;: 31.329899904546373,</td></tr><tr><td>&quot;gcj02Lon&quot;: 120.81065731269604,</td></tr><tr><td>&quot;gcj02Lat&quot;: 31.327857527712254</td></tr><tr><td>},</td></tr><tr><td>{</td></tr><tr><td>&quot;lon&quot;: 120.80635150669826,</td></tr><tr><td>&quot;lat&quot;: 31.329899904546373,</td></tr><tr><td>&quot;gcj02Lon&quot;: 120.81065731269604,</td></tr><tr><td>&quot;gcj02Lat&#x27;: 31.327857527712254</td></tr><tr><td>},</td></tr><tr><td>{</td></tr><tr><td>&quot;lon&quot;: 120.80627826279631,</td></tr><tr><td>&quot;lat&quot;: 31.330094321398644,</td></tr><tr><td>&quot;gcj02Lon&quot;: 120.81058422868627,</td></tr><tr><td>&quot;gcj02Lat&quot;: 31.328052139121546</td></tr><tr><td>}</td></tr><tr><td>]</td></tr><tr><td>}</td></tr><tr><td>]</td></tr><tr><td>}</td></tr></table>

## 17. 停靠点

17.1 新增停靠点

测试环境 URL:

https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/stop/ad
d 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/stop/add 

Content-Type: application/json 

请求方式：post

接口说明：新增一个取货点到指定站点。这个点需要执行【18】才能让车辆使用。

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>name</td><td>取货点1</td><td>String</td><td>是</td><td>取货点名字,不能和系统中已有的重复</td></tr><tr><td>lat</td><td>32.0000752263489200</td><td>Double</td><td>是</td><td>取货点纬度</td></tr><tr><td>lon</td><td>120.9286306793417700</td><td>Double</td><td>是</td><td>取货点经度</td></tr><tr><td>heading</td><td>2.918649056597483</td><td>Double</td><td>是</td><td>车头朝向,弧度制。正东为0,逆时针为正,顺时针为负。例:1.57为正北,3.14为正西,-1.57为正南,-3.14为正西</td></tr><tr><td>type</td><td>PASS_BY</td><td>String</td><td>否</td><td>默认null的时候表示新增取货点。PARKING:取货点PASS_BY:经停点</td></tr><tr><td>stationId</td><td>1091</td><td>Integer</td><td>是</td><td>所属站点。站点属于您的公司</td></tr></table>


让物流更简单


<table><tr><td>comment</td><td>测试备注</td><td>String</td><td>否</td><td>备注说明。40个字符。</td></tr><tr><td>userId</td><td>15665</td><td>Integer</td><td>是</td><td>操作人id。客户系统中的id。只做存储,不做合法性校验。必填。</td></tr><tr><td>userName</td><td>张三</td><td>String</td><td>是</td><td>操作人姓名。客户系统中的id。只做存储,不做合法性校验。必填。</td></tr></table>

请求示例:

```txt
"stationId": 1091,
"name": "api 测试停靠点",
"lat": 32.0000752263489200,
"lon": 120.9286306793417700,
"heading": 2.918649056597483,
"comment": "testaaaa",
"userId": 1,
"userName": "张三"
```

## 返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td></td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>1005</td><td>Integer</td><td>增加成功的停靠点id</td></tr></table>

返回示例：(200) 成功

```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": 1005
} 
```

17.2 修改停靠点

测试环境 URL:

```txt
https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/stop/update 
```

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/stop/update
Content-Type: application/json 

请求方式：put

接口说明：更新取货点的各参数。停靠点的类型不可以改变。


请求体参数说明：


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>id</td><td>20141</td><td>Integer</td><td>是</td><td>需要修改的停靠点id</td></tr><tr><td>name</td><td>取货点1</td><td>String</td><td>是</td><td>取货点名字,不能和系统中已有的重复</td></tr><tr><td>lat</td><td>32.1111</td><td>Double</td><td>是</td><td>取货点纬度</td></tr><tr><td>lon</td><td>120.222</td><td>Double</td><td>是</td><td>取货点经度</td></tr><tr><td>heading</td><td>2.3333</td><td>Double</td><td>是</td><td>车头朝向,弧度制。正东为0,逆时针为正,顺时针为负。例:1.57为正北,3.14为正西,-1.57为正南,-3.14为正西</td></tr><tr><td>stationId</td><td>1091</td><td>Integer</td><td>是</td><td>所属站点。站点属于</td></tr></table>


让物流更简单


<table><tr><td></td><td></td><td></td><td></td><td>您的公司</td></tr><tr><td>comment</td><td>测试备注</td><td>String</td><td>否</td><td>备注说明。40个字符。</td></tr><tr><td>userId</td><td>15665</td><td>Integer</td><td>是</td><td>操作人id。客户系统中的id。只做存储,不做合法性校验。必填。</td></tr><tr><td>userName</td><td>张三</td><td>String</td><td>是</td><td>操作人姓名。客户系统中的id。只做存储,不做合法性校验。必填。</td></tr></table>

## 请求示例:

```javascript
"id": 22141,
"name": "api 测试停靠点更新",
"lat": 32.111111,
"lon": 120.22222,
"heading": 2.333333,
"comment": "testaaaa 更新",
"userId": 1,
"userName": "李四"
```


返回参数说明：(200) 成功


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td></td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td></td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td></td><td>接口返回对象</td></tr></table>

## 让物流更简单


返回示例：(200) 成功


```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": null
} 
```

## 17.3 删除停靠点

测试环境 URL:

```txt
https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/stop/delete 
```

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/stop/delete
Content-Type: application/json 

请求方式: delete

接口说明：删除指定的取货点。这个取货点不能被车辆绑定，不能处于任何任务中。

请求体参数说明：

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>id</td><td>20141</td><td>Integer</td><td>是</td><td>需要修改的停靠点id</td></tr><tr><td>userId</td><td>15665</td><td>Integer</td><td>是</td><td>操作人 id。客户系统中的 id。只做存储,不做合法性校验。必填。</td></tr><tr><td>userName</td><td>张三</td><td>String</td><td>是</td><td>操作人姓名。客户系统中的 id。只做存储,不做合法性校验。必填。</td></tr></table>

请求示例:

```txt
https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/ 
```

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>请求失败</td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>String</td><td>接口返回对象</td></tr></table>

## 18. 配置车辆和停靠点的关联关系

测试环境 URL:

/vehicleStop/addVehicleStopLink 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/ 

请求方式: post

接口说明：配置车辆和停靠点的绑定关系

URI 参数说明:

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00075</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>userName</td><td>用户</td><td>String</td><td>是</td><td>操作用户</td></tr><tr><td>stopId</td><td>123</td><td>Integer</td><td>是</td><td>停靠点 id</td></tr><tr><td>waitingTime</td><td>1</td><td>Integer</td><td>是</td><td>取货完等待时长(分)</td></tr></table>

```json
{
    "vehicleName": "xxxxx",
    "userName": "zelos",
    "stopId": 1,
    "waitingTime": 1
} 
```

请求示例:

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>Null</td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": null
} 
```

## 19. 解除车辆和停靠点的关联关系

测试环境 URL:

正式环境 URL:

```txt
"success": true, "errorCode": null, 
```

https://gateway.zelostech.com.cn/business-proxy/open-apis/vehicleStop 

/deleteVehicleStopLink 

请求方式: delete

接口说明：解除车辆和停靠点的绑定关系

URI 参数说明:

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>vehicleName</td><td>ZL00075</td><td>String</td><td>是</td><td>车辆名称</td></tr><tr><td>userName</td><td>用户</td><td>String</td><td>是</td><td>操作用户</td></tr><tr><td>stopId</td><td>123</td><td>Integer</td><td>是</td><td>停靠点 id</td></tr></table>

## 请求示例:

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>Null</td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

让物流更简单

## 20. 经停点

20.1 配置经停点

测试环境 URL:

https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/stopPassBy/config 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/stopPassBy/config 

请求方式: post

接口说明：给某两个点配置一些经停点，用于制定行驶路线。可以新增，删除和修改。


URI 参数说明:


<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>fromStopId</td><td>227122</td><td>String</td><td>是</td><td>起点停靠点 id</td></tr><tr><td>toStopId</td><td>227121</td><td>String</td><td>是</td><td>终点停靠点 id</td></tr><tr><td>passStopIds</td><td>[152858]</td><td>Array</td><td>否</td><td>经停点 id,数组中的顺序就是规划路径经过的顺序。当这一项为空的时候,将删除起点和终点的经停配置</td></tr><tr><td>userName</td><td>张三</td><td>String</td><td>是</td><td>操作用户用户名</td></tr></table>

请求示例:

"fromStopId": 227122, 

```javascript
"success": true, "errorCode": null, "message": null, "data": null 
```


让物流更简单


```txt
"toStopId": 227121, "passStopIds": [152858], "userName": "张三"
```

返回参数说明：(200) 成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>Null</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>Null</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td>null</td><td>Null</td><td>接口返回对象</td></tr></table>

返回示例：(200) 成功

## 20.2 查询经停点

测试环境 URL:

https://gateway-uat.zelostech.com.cn/business-proxy/open-apis/stopPassBy/query 

正式环境 URL:

https://gateway.zelostech.com.cn/business-proxy/open-apis/stopPassBy/query 

请求方式: get

接口说明：查询两个点的经停配置。返回值中 null 的字段请忽略。

URI 参数说明:

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>是否必填</td><td>参数描述</td></tr><tr><td>fromStopId</td><td>227122</td><td>String</td><td>是</td><td>起点停靠点 id</td></tr><tr><td>toStopId</td><td>227121</td><td>String</td><td>是</td><td>终点停靠点 id</td></tr></table>

请求示例:

```json
{
    "fromStopId": 227122,
    "toStopId": 227121
} 
```

返回参数说明：(200)成功

<table><tr><td>参数名</td><td>示例值</td><td>参数类型</td><td>参数描述</td></tr><tr><td>success</td><td>true</td><td>Boolean</td><td>成功与否</td></tr><tr><td>errorCode</td><td>null</td><td>String</td><td>接口失败,错误码</td></tr><tr><td>message</td><td>null</td><td>String</td><td>接口失败的情况下,错误描述</td></tr><tr><td>data</td><td></td><td>Object</td><td>接口返回对象</td></tr><tr><td>data.id</td><td>12345</td><td>Integer</td><td>经停配置 id</td></tr><tr><td>data.fromStopId</td><td>227122</td><td>Integer</td><td>起点停靠点 id,跟入参一致</td></tr><tr><td>data.fromStopName</td><td>起点</td><td>String</td><td>查询的起点的停靠点名称</td></tr><tr><td>data.toStopId</td><td>227121</td><td>Integer</td><td>终点停靠点 id,跟入参一致</td></tr><tr><td>data.to StopName</td><td>终点</td><td>String</td><td>查询的终点的停靠点名称</td></tr></table>


让物流更简单


<table><tr><td>data.createUserName</td><td>张三</td><td>String</td><td>做此配置的用户名称</td></tr><tr><td>data.passedStops</td><td></td><td>Array</td><td>经停点的数组</td></tr><tr><td>data.passedStops.passStopId</td><td>152858</td><td>Integer</td><td>经停点 id</td></tr><tr><td>data.passedStops.passStopName</td><td>SSS</td><td>String</td><td>经停点名称</td></tr><tr><td>data.passedStops.passIndex</td><td>0</td><td>Integer</td><td>经过顺序。从 0 开始。</td></tr><tr><td>data.passedStops.heading</td><td>-0.06978127946660084</td><td>Double</td><td>朝向弧度</td></tr><tr><td>data.passedStops.gcj02Lat</td><td>33.65321154446</td><td>Double</td><td>gcj02 坐标系纬度</td></tr><tr><td>data.passedStops.gcj02Lon</td><td>119.09925282254</td><td>Double</td><td>gcj02 坐标系经度</td></tr></table>


返回示例：(200) 成功


```json
{
    "success": true,
    "errorCode": null,
    "message": null,
    "data": {
    "id": 12345,
    "fromStopId": 227122,
    "fromStopName": "起点",
    "toStopId": 227121,
    "toStopName": "终点",
    "createUserName": "张三",
    "passedStops": [
    {
    "passStopId": 152858,
    "passStopName": "SSS",
    "passIndex": 0,
    "heading": -0.06978127946660084,
    "gcj02Lat": 33.11103333,
    "gcj02Lon": 119.352332332,
```

## 环境资料

1. 认证用的 appid

- 跟相关对接人员（例如销售）申请。下载地址：运营管理平台->用户权限->公司对接配置。搜索公司名称，点击对应的图标下载。

<table><tr><td>ID</td><td>公司名称</td><td>操作</td></tr><tr><td>1</td><td></td><td></td></tr><tr><td>8</td><td></td><td></td></tr><tr><td>9</td><td></td><td></td></tr><tr><td>17</td><td></td><td></td></tr><tr><td>21</td><td></td><td></td></tr><tr><td>25</td><td></td><td></td></tr><tr><td>27</td><td></td><td></td></tr><tr><td>28</td><td></td><td></td></tr><tr><td>29</td><td></td><td></td></tr><tr><td>31</td><td></td><td></td></tr></table>

## 2. 服务器地址

测试环境域名：https://gateway-uat.zelostech.com.cn

正式环境域名：https://gateway.zelostech.com.cn

## 附录

1. 车辆业务状态列表(vehicleBusinessStatus)

<table><tr><td>返回码</td><td>说明</td></tr><tr><td>IDLE</td><td>空闲,车辆没有任务</td></tr><tr><td>WITHORDERS</td><td>装货,车辆派单或装单</td></tr><tr><td>ONWAY</td><td>去程,车辆自动驾驶中</td></tr></table>


让物流更简单


<table><tr><td>ATSTOP</td><td>投递,车辆处于取货点,等待用户</td></tr><tr><td>BACK</td><td>回站,车辆在返回装货点途中</td></tr><tr><td>ARRIVEHOME</td><td>到站,车辆到达了装货点,且车上有某个停靠点揽件的订单。</td></tr><tr><td>ROUTING</td><td>规划中,车辆在进行路径规划过程中。下一个状态是去程。</td></tr></table>

## 2. 常用指令类型

<table><tr><td>返回码</td><td>说明</td><td>是否特殊指令</td><td>备注</td></tr><tr><td>BUSINESS_SHUTDOWN</td><td>关机</td><td>否</td><td>车辆需在线,车速需为0,不能处于遥控中,不能处于升级中</td></tr><tr><td>RESTART_HARD</td><td>硬重启</td><td>否</td><td>车辆需在线,车速需为0,不能处于遥控中,不能处于升级中</td></tr><tr><td>RESTART_SOFT</td><td>软重启</td><td>否</td><td>车辆需在线,车速需为0,不能处于遥控中,不能处于升级中</td></tr><tr><td>EMERGENCY_STOP</td><td>车辆刹车</td><td>是</td><td>车辆需在线。此指令会将车速置0(急刹),操作之前务必保证车后方空旷。停车后如果需要继续自驾,可以发送【RECOVERY】指令</td></tr><tr><td>RECOVERY</td><td>车辆继续行走</td><td>是</td><td>车辆需在线。刹车的车辆恢复自动驾驶。</td></tr><tr><td>BUSINESS_TBOX_SET_UP</td><td>远程上电(开机)</td><td>否</td><td>车辆需要有tbox设备,且tbox在线。</td></tr><tr><td>BUSINESS_ARRIVE</td><td>车辆到达</td><td>否</td><td>服务端会开始执行车辆到达下一个目的地的逻辑。</td></tr></table>


让物流更简单


<table><tr><td>BUSINESS_ONE_KEY_SIDE</td><td>靠边停车</td><td>否</td><td>车辆会自动减速且靠边停车。如果需要车辆继续行走,可以发送【BUSINESS_TASK_RECOVERY】指令</td></tr><tr><td>BUSINESS_TASK_RECOVERY</td><td>重新起步</td><td>否</td><td>让停下来的车辆,继续执行任务。例如靠边停车之后的车辆危险解除后。</td></tr></table>

## 3. 名词解释

公司：客户跟九识签完合同之后，每一份合同在系统中对应的实体叫做【公司】。每一个公司有一份对接密钥。可以在运营管理平台->用户权限->公司机构中查看到自己的公司。

站点：运营范围。一个公司可以有多个站点。一个站点下面有多个停靠点。车辆属于站点，车辆能绑定所属站点的若干个停靠点，并且在这些停靠点中间发送任务。

停靠点：默认表示车辆能够停靠的点位。车辆需要绑定了停靠点才能在发布任务的时候选中。每个车辆可以绑定任意个停靠点（接口 18）。

经停点：用户规划车辆路径。车辆会经过这个点位，但是不会停下来。每个经停点属于一个起点和和一个终点。一个起点和一个终点可以有序配置多个经停点。

例如，可以给 A 小区南门（取货点）和 B 小区西门（取货点）依次配置十字路

口 1（经停点），公园大门（经停点）。那么当车辆发送的任务是 A 小区南门到

B 小区西门的时候，规划的路径会依次经过十字路口 1、公园大门再到达 B 小区西门。

特殊指令：有使用限制的指令。例如可能需要签署相关协议。具体可以咨询对接销售。