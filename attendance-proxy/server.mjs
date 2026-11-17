import http from 'node:http';
import {timingSafeEqual} from 'node:crypto';
const token=process.env.PROXY_TOKEN;
const send=(res,status,data)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(data));};
http.createServer(async(req,res)=>{
 if(req.url==='/health')return send(res,200,{ok:true,protected:Boolean(token)});
 if(req.url!=='/attendance'||req.method!=='POST')return send(res,404,{error:'Not found'});
 const a=Buffer.from(req.headers.authorization||''),b=Buffer.from('Bearer '+token);
 if(!token||a.length!==b.length||!timingSafeEqual(a,b))return send(res,401,{error:'Unauthorized'});
 try{let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>4096)return send(res,413,{error:'Request too large'});}const v=JSON.parse(raw);const roll=String(v.vvchRollNo||'').trim(),sem=Number(v.vintSemester);
 if(!/^[a-z0-9-]{4,25}$/i.test(roll)||!Number.isInteger(sem)||sem<1||sem>8)return send(res,400,{error:'Invalid roll number or semester'});
 const upstream=await fetch('https://gietuerp.in/AttendanceReport/GetAttendanceByRollNo',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({vvchRollNo:roll,vintSemester:String(sem),vdtmStartDate:'',vdtmEndDate:''}),signal:AbortSignal.timeout(45000),redirect:'error'});
 if(!upstream.ok)return send(res,502,{error:'ERP returned HTTP '+upstream.status});let data=await upstream.json();if(typeof data==='string')data=JSON.parse(data);if(!Array.isArray(data.dataAttendance))return send(res,502,{error:'Unexpected ERP response'});send(res,200,{dataAttendance:data.dataAttendance});
 }catch{send(res,502,{error:'ERP unavailable or timed out'});}
}).listen(3000,'0.0.0.0',()=>console.log('GIET attendance proxy on 3000; token protection: '+Boolean(token)));
