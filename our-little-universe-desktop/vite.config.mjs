import {defineConfig} from 'vite';
import {VitePWA} from 'vite-plugin-pwa';
export default defineConfig({
 base:'./',
 plugins:[VitePWA({
  registerType:'autoUpdate',includeAssets:['icon.svg'],
  manifest:{name:'Our Little Universe',short_name:'Our Universe',
   description:'Your private family memory archive',
   start_url:'./',scope:'./',display:'standalone',
   background_color:'#090d20',theme_color:'#101634',
   icons:[{src:'icon.svg',sizes:'any',type:'image/svg+xml',purpose:'any maskable'}]},
  workbox:{globPatterns:['**/*.{html,js,css,svg}'],maximumFileSizeToCacheInBytes:5000000}
 })],
 build:{target:'es2020',outDir:'dist'}
});
