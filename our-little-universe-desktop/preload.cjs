'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('ourHub',{
 info:()=>ipcRenderer.invoke('family-hub-info'),
 invite:(name,role)=>ipcRenderer.invoke('family-hub-invite',String(name||''),String(role||'contributor')),
 devices:()=>ipcRenderer.invoke('family-hub-devices'),
 revoke:id=>ipcRenderer.invoke('family-hub-revoke',String(id||'')),
 startup:enabled=>ipcRenderer.invoke('family-hub-autostart',!!enabled),
 relayStatus:()=>ipcRenderer.invoke('family-relay-status'),
 relayDeploy:(accountId,apiToken)=>ipcRenderer.invoke('family-relay-deploy',String(accountId),String(apiToken)),
 relayPause:()=>ipcRenderer.invoke('family-relay-pause'),
 relayResume:()=>ipcRenderer.invoke('family-relay-resume')
});
