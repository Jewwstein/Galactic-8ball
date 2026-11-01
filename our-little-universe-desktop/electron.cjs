const {app,BrowserWindow,shell,session}=require('electron');
const path=require('path');
app.setName('Our Little Universe');
app.whenReady().then(()=>{
 const win=new BrowserWindow({width:1180,height:840,minWidth:720,minHeight:520,
  backgroundColor:'#090d20',title:'Our Little Universe',
  webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true}});
 win.loadFile(path.join(__dirname,'dist','index.html'));
 win.webContents.setWindowOpenHandler(({url})=>{
  if(url.startsWith('https://'))shell.openExternal(url);
  return {action:'deny'};});
 session.defaultSession.setPermissionRequestHandler((_webContents,permission,cb)=>cb(permission==='media'));
});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});
