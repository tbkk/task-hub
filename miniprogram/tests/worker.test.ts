import test from 'node:test'
import assert from 'node:assert/strict'
import { canSelectSlot, canSelectStop, bookingDates } from '../src/features/catalog/model'
import { validateOrderDraft, canCancel, cancellationText, orderStatusText } from '../src/features/order/model'
import { Submission } from '../src/features/order/submission'
const slot = { id:'s',start:'2030-01-01T09:00:00+08:00',end:'2030-01-01T09:30:00+08:00',available:true,remaining:1 }
test('未来半小时时段同时校验容量和服务端可用性', () => {
  const now = Date.parse('2030-01-01T08:59:00+08:00')
  assert.equal(canSelectSlot(slot,now),true)
  for (const changed of [{available:false},{remaining:0},{end:'2030-01-01T10:00:00+08:00'},{start:'2030-01-01T08:00:00+08:00'}]) assert.equal(canSelectSlot({...slot,...changed},now),false)
  assert.deepEqual(bookingDates(2,Date.parse('2029-12-31T17:00:00Z')),['2030-01-01','2030-01-02'])
})
const draft = { warehouseId:'w',stopId:'p',slotId:'s',description:'轴承',size:'2箱',receiverName:'接收人',receiverPhone:'13800000000',remark:'' }
const rules = {bookingDays:7,descriptionMaxLength:100,sizeMaxLength:100,remarkMaxLength:100}
test('申请保留未匹配接收人且验证必填手机号与长度',()=>{
 assert.deepEqual(validateOrderDraft(draft,rules),{})
 assert.equal(validateOrderDraft({...draft,receiverPhone:'12'},rules).receiverPhone,'请输入正确手机号')
 assert.ok(validateOrderDraft({...draft,description:' '.repeat(2)},rules).description)
 assert.ok(validateOrderDraft({...draft,remark:'x'.repeat(101)},rules).remark)
})
test('取消权限需要申请人及服务端动作，取消处理中不覆盖主状态',()=>{
 const order={applicantId:'me',allowedActions:['ORDER_CANCEL'],cancellation:null}
 assert.equal(canCancel(order,'me'),true)
 assert.equal(canCancel(order,'other'),false)
 assert.equal(canCancel({...order,allowedActions:[]},'me'),false)
 assert.equal(canCancel({...order,cancellation:{status:'PENDING'}},'me'),false)
 assert.equal(orderStatusText('ACCEPTED'),'已受理')
 assert.equal(cancellationText('PENDING'),'取消处理中')
})
test('未知提交锁定正文及幂等键，重试不能换正文或并发',async()=>{
 const submission=new Submission<typeof draft>();const calls:unknown[]=[]
 await assert.rejects(submission.run(draft,async(body,key)=>{calls.push([body,key]);throw new Error('断网')}))
 assert.equal(submission.pending,true)
 await assert.rejects(submission.run({...draft,size:'3箱'},async()=>null),/原申请/)
 await submission.run(draft,async(body,key)=>{calls.push([body,key]);return 'saved'})
 assert.deepEqual(calls[0],calls[1]);assert.equal(submission.pending,false)
})
test('停用或不可达的收藏不可作为目的地',()=>{
 assert.equal(canSelectStop({id:'s',name:'站点',enabled:false}),false)
 assert.equal(canSelectStop({id:'s',name:'站点',available:false}),false)
})
test('明确时段拒绝后可编辑重提，正在执行时拦截并发',async()=>{
 const submission=new Submission<typeof draft>();const keys:string[]=[]
 await assert.rejects(submission.run(draft,async(_,key)=>{keys.push(key);throw Object.assign(new Error('时段不可用'),{statusCode:422})}))
 assert.equal(submission.pending,false)
 let resolve!:(value:string)=>void
 const pending=submission.run({...draft,slotId:'new'},async(_,key)=>{keys.push(key);return new Promise<string>(r=>{resolve=r})})
 await assert.rejects(submission.run({...draft,slotId:'new'},async()=>''),/重复操作/)
 resolve('saved');await pending
 assert.notEqual(keys[0],keys[1])
})
test('目录与订单 API 保留字符串 ID、参数编码、工作区和动作版本',async()=>{
 const calls:any[]=[]
 ;(globalThis as any).uni={request(options:any){calls.push(options);options.success({statusCode:200,data:{code:0,message:'ok',data:[]}})}}
 const {listSlots,setFavorite}=await import('../src/features/catalog/api')
 const {createOrder,requestCancellation,listOrders}=await import('../src/features/order/api')
 await listSlots('warehouse/1','stop&2','2030-01-01')
 await setFavorite('stop/2',true)
 await createOrder(draft,'key-create')
 await requestCancellation('9007199254740993',3,'原因','key-cancel')
 await listOrders({status:'PENDING',keyword:'轴承 & 箱',page:2,pageSize:20})
 assert.ok(calls[0].url.includes('warehouseId=warehouse%2F1&stopId=stop%262'))
 assert.ok(calls[1].url.endsWith('/favorites/stop%2F2'))
 assert.equal(calls[2].header['Idempotency-Key'],'key-create')
 assert.equal(calls[3].header['X-Workspace'],'worker')
 assert.ok(calls[3].url.endsWith('/orders/9007199254740993/cancellations'))
 assert.deepEqual(calls[3].data,{expectedVersion:3,reason:'原因'})
 assert.ok(calls[4].url.includes('page=2'))
})
