'use strict';
const {app,BrowserWindow,shell,session,ipcMain}=require('electron');
const path=require('path');
const fs=require('fs');
const {agent}=require('./relay-agent.cjs');
const {deploy}=require('./relay-setup.cjs');
const {hub}=require('./hub-server.cjs');
let familyHub,hubStatus,window,relay;
app.setName('Our Little Universe');
app.whenReady().then(async()=>{
 familyHub=hub(path.join(app.getPath('userData'),'family-hub'));
 try{hubStatus=await familyHub.start();}
 catch(error){hubStatus={error:error.message};}
 relay=agent(path.join(app.getPath('userData'),'family-hub'),{
  onState:state=>{if(window&&!window.isDestroyed())window.webContents.send('family-relay-status',state);}
 });
 relay.start();
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
 ipcMain.handle('family-relay-status',()=>relay.state());
 ipcMain.handle('family-relay-deploy',async(_event,accountId,apiToken)=>{
  // One-time PC-only deployment. The hosting token is not saved or forwarded.
  const settings=await deploy({accountId:String(accountId||''),apiToken:String(apiToken||'')});
  return relay.configure(settings);
 });
 ipcMain.handle('family-relay-pause',()=>{relay.stop();return relay.state();});
 ipcMain.handle('family-relay-resume',()=>relay.start());
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
