'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('ourHub',{
 info:()=>ipcRenderer.invoke('family-hub-info'),
 invite:(name,role)=>ipcRenderer.invoke('family-hub-invite',String(name||''),String(role||'contributor')),
 devices:()=>ipcRenderer.invoke('family-hub-devices'),
 revoke:id=>ipcRenderer.invoke('family-hub-revoke',String(id||'')),
 startup:enabled=>ipcRenderer.invoke('family-hub-autostart',!!enabled),
 privateNetwork:()=>ipcRenderer.invoke('family-hub-private-network')
});
