'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('ourHub',{
 info:()=>ipcRenderer.invoke('family-hub-info'),
 invite:(name,role)=>ipcRenderer.invoke('family-hub-invite',String(name||''),String(role||'contributor')),
 devices:()=>ipcRenderer.invoke('family-hub-devices'),
 revoke:id=>ipcRenderer.invoke('family-hub-revoke',String(id||'')),
 startup:enabled=>ipcRenderer.invoke('family-hub-autostart',!!enabled),
 directStatus:()=>ipcRenderer.invoke('family-direct-status'),
 directEnable:(manual=false,ip='')=>ipcRenderer.invoke('family-direct-enable',!!manual,String(ip)),
 directDisable:()=>ipcRenderer.invoke('family-direct-disable')
});
