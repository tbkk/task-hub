import { newIdempotencyKey } from '../common/idempotency'
// 未知结果不能通过编辑正文或新 key 绕过同一次确认。
export class Submission<T> {
 private attempt:{key:string;body:T;serialized:string}|null=null
 busy=false
 get pending(){return this.attempt!==null}
 async run<R>(input:T,send:(input:T,key:string)=>Promise<R>,keepPending:(result:R)=>boolean=()=>false):Promise<R>{
  if(this.busy) throw new Error('正在提交，请勿重复操作')
  const serialized=JSON.stringify(input)
  if(this.attempt && this.attempt.serialized!==serialized) throw new Error('请先重试原申请，核对提交结果')
  this.attempt??={key:newIdempotencyKey(),body:JSON.parse(serialized),serialized}
  this.busy=true
  try{const result=await send(this.attempt.body,this.attempt.key);if(!keepPending(result))this.attempt=null;return result}
  catch(cause){
   const status=(cause as {statusCode?:number}).statusCode
   // 明确的 4xx 拒绝不产生业务成功；5xx/网络错误继续保留原 key。
   if(status && status>=400 && status<500 && status!==408) this.attempt=null
   throw cause
  }finally{this.busy=false}
 }
}
