'use strict';
const {app,BrowserWindow,shell,session,ipcMain}=require('electron');
const path=require('path');
const fs=require('fs');
const {execFile}=require('child_process');
const {hub}=require('./hub-server.cjs');
let familyHub,hubStatus,window;
app.setName('Our Little Universe');
app.whenReady().then(async()=>{
 familyHub=hub(path.join(app.getPath('userData'),'family-hub'));
 try{hubStatus=await familyHub.start();}
 catch(error){hubStatus={error:error.message};}
 ipcMain.handle('family-hub-info',async()=>({
  ...hubStatus,url:'http://127.0.0.1:43877',
  devices:familyHub.devices(),autoStart:app.getLoginItemSettings().openAtLogin
 }));
 ipcMain.handle('family-hub-invite',(_event,name,role)=>familyHub.invite(name,role));
 ipcMain.handle('family-hub-devices',()=>familyHub.devices());
 ipcMain.handle('family-hub-revoke',(_event,id)=>familyHub.revoke(id));
 ipcMain.handle('family-hub-autostart',(_event,enabled)=>{
  app.setLoginItemSettings({openAtLogin:enabled});return app.getLoginItemSettings().openAtLogin;
 });
 ipcMain.handle('family-hub-private-network',async()=>{
  const exe=process.platform==='win32'?path.join(process.env.ProgramFiles||'C:\\Program Files','Tailscale','tailscale.exe'):'tailscale';
  const command=fs.existsSync(exe)?exe:'tailscale';
  const execute=(args)=>new Promise((resolve,reject)=>execFile(command,args,{timeout:45000,windowsHide:true},(error,stdout,stderr)=>{
   if(error)reject(new Error((stderr||stdout||error.message).trim()));
   else resolve((stdout||stderr||'').trim());
  }));
  try{
   const started=await execute(['serve','--bg','43877']);
   const status=await execute(['serve','status']);
   const location=(started+' '+status).match(/https:\/\/[A-Za-z0-9.-]+\.ts\.net/);
   return {ok:true,url:location?.[0]||'',status:status||started};
  }catch(error){
   return {ok:false,error:error.message,help:'Install Tailscale on this PC, sign in, and make sure HTTPS is enabled.'};
  }
 });
 window=new BrowserWindow({
  width:1180,height:840,minWidth:720,minHeight:520,
  backgroundColor:'#090d20',title:'Our Little Universe',
  webPreferences:{
   preload:path.join(__dirname,'preload.cjs'),
   nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true
  }
 });
 window.loadFile(path.join(__dirname,'dist','index.html'));
 window.webContents.setWindowOpenHandler(({url})=>{
  if(url.startsWith('https://'))shell.openExternal(url);
  return {action:'deny'};
 });
 session.defaultSession.setPermissionRequestHandler((_contents,permission,callback)=>
  callback(permission==='media'));
});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});
