// H5 浏览器验收：接口桩仅验证页面重试、会话恢复和窄屏；数据库闭环见 DeliveryFlowIT。
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try {
 const page=await browser.newPage({viewport:{width:390,height:844}});
 const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 const user={id:'acceptance-user',name:'验收调度员',verifiedPhone:'13900000001',grants:[{role:'dispatch',scope:'WAREHOUSES',warehouseIds:['w']}],platformGrants:[],admin:false,mustChangePassword:false};
 const session={token:'acceptance-browser-fixture',expiresAt:new Date(Date.now()+3600000).toISOString(),user};
 await page.addInitScript(s=>{if(!localStorage.getItem('seeded')){localStorage.setItem('task-hub:session',JSON.stringify(s));localStorage.setItem('task-hub:session-token',JSON.stringify({token:s.token,expiresAt:s.expiresAt}));localStorage.setItem('seeded','yes')}},session);
 const version=4; const calls=[];const authenticatedReads=[];
 await page.route('**/api/**',async route=>{
  const req=route.request(), path=new URL(req.url()).pathname; let data; if(!path.startsWith("/api/")){await route.continue();return;}
  if(path==='/api/tasks/task-1')authenticatedReads.push(req.headers().authorization);
  if(path==='/api/identity/me')data=user;
  else if(path==='/api/tasks/task-1')data={id:'task-1',vehicleId:'vehicle-1',batchId:'batch-1',state:'AT_STOP',currentStopId:'s1',nextStopId:'s2'};
  else if(path==='/api/batches/batch-1')data={id:'batch-1',version};
  else if(path==='/api/tasks/task-1/go'){calls.push({key:req.headers()['idempotency-key'],body:req.postData()});if(calls.length===1){await route.abort('failed');return;}data={id:'control-1',status:'ACCEPTED'};}
  else data={items:[],page:1,pageSize:20,total:0};
  await route.fulfill({json:{code:0,message:'成功',data}});
 });
 await page.goto((process.env.H5_ACCEPTANCE_URL || 'http://127.0.0.1:18191') + '/#/subpackages/dispatch/task-detail?id=task-1');
 await page.getByText('继续出发',{exact:true}).waitFor();
 await page.getByText('继续出发',{exact:true}).click();
 await page.getByText('重试同一请求',{exact:true}).waitFor();
 await page.getByText('重试同一请求',{exact:true}).click();
 await page.getByText('继续出发',{exact:true}).waitFor();
 assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);
 assert.ok(calls[0].key, '请求必须携带非空幂等键');
 const readsBeforeReload=authenticatedReads.length;
 await page.reload();await page.getByText('你好，验收调度员',{exact:true}).waitFor();
 await page.getByText('继续出发',{exact:true}).waitFor();
 await page.getByText('task-1',{exact:true}).waitFor();
 assert.ok(authenticatedReads.length>readsBeforeReload);
 assert.equal(authenticatedReads.at(-1),`Bearer ${session.token}`);
 for(const width of [320,390]){await page.setViewportSize({width,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);}
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({networkRetry:'PASS',sameKeyAndBody:'PASS',sessionReload:'PASS',width320and390:'PASS',pageErrors:errors}));
 } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exit(1)});
