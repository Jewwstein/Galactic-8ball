import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const ui=fs.readFileSync(path.join(import.meta.dirname,'../src/main.js'),'utf8');
test('Family invitation is an app-native form, never an unsupported Electron prompt',()=>{
 assert.match(ui,/id="familyInviteForm"/);
 assert.match(ui,/name="label"/);
 assert.match(ui,/name="role"/);
 assert.match(ui,/data-command/);
 assert.match(ui,/window\.ourHub\.invite\(label,role\)/);
 assert.match(ui,/latestInviteLink=base\+'#'\+payload\.toString\(\)/);
 assert.match(ui,/id="familyInvitationLink"/);
 assert.doesNotMatch(ui,/\b(?:window\.)?(?:prompt|confirm|alert)\s*\(/);
});
test('All confirmation actions have an in-app modal and buttons do not submit forms accidentally',()=>{
 assert.match(ui,/function confirmInsideApp\(/);
 assert.match(ui,/if\(name==='confirm-yes'\)/);
 assert.match(ui,/if\(name==='confirm-cancel'\)/);
 assert.match(ui,/type="button".*data-command=/);
 assert.match(ui,/confirmInsideApp\('Revoke this family device/);
 assert.match(ui,/confirmInsideApp\('Erase this memory forever/);
});
