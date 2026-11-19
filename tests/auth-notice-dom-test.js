// Offline simulated DOM fixture with realistic cell geometry. No live school account or network.
const { execFileSync } = require('node:child_process');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const cp = process.argv[2];
const command = (...args) => execFileSync('java', ['-cp', cp, 'com.mutang.xunxi.AuthNoticeParserTest', ...args], { encoding: 'utf8' });
function grid(headers, data) {
  const doc = { location: { href: 'https://portal.test/list' }, defaultView: { getComputedStyle: () => ({visibility:'visible', display:'block'}) } };
  const element = (label, i, title) => ({ innerText:label, ownerDocument:doc, cellIndex:i, style:{}, getBoundingClientRect:()=>({left:i*180,right:(i+1)*180,width:180,height:40}), getAttribute:k=>k==='title'?(title||null):null, querySelector:()=>null, scrollIntoView(){this.scrolled=true;} });
  const scope = { querySelectorAll: () => headers.map((h,i)=>element(h,i)) };
  const rows=data.map(record=>{
    const cells=record.map((c,i)=>typeof c==='string'?element(c,i):element(c[0],i,c[1]));
    return { ...element('',0), cells, querySelectorAll:()=>cells, closest:()=>scope };
  });
  doc.querySelector=()=>null;
  doc.querySelectorAll=s=>s.startsWith('tr,')?rows:[];
  return { doc, rows };
}
function capture(g){return JSON.parse(vm.runInNewContext(command('--capture'),{document:g.doc}));}
const g=grid(['操作','标题','发布时间','发布单位'],[
  ['详情',['2026年秋季...', '2026年秋季学期网络课程通知'],'2023-12-03 09:51:31','教务处'],
  ['详情','学生选课手册新','2026-06-24 15:24:10','教务处']
]);
const result=capture(g);
assert.deepEqual(result.filter(r=>r.row).map(r=>r.title),['2026年秋季学期网络课程通知','学生选课手册新']);
assert.equal(result[0].date,'2023-12-03 09:51:31');
assert.equal(vm.runInNewContext(command('--locate','学生选课手册新'),{document:g.doc}),'found');
assert.equal(g.rows[1].cells[0].scrolled,true);
assert.equal(vm.runInNewContext(command('--locate','不存在的标题'),{document:g.doc}),'not-found');
const fallback=capture(grid([], [['详情','学生选课手册新','2023-12-03 09:51:31']]));
assert.equal(fallback[0].title,'学生选课手册新');
const reordered=capture(grid(['选择','操作','状态','类型','标题','时间'],[['','查看详情','已读','考务管理','课程通知标题新','2023-12-03 09:51:31']]));
assert.equal(reordered[0].title,'课程通知标题新');
console.log('PASS DOM simulation: title column, date, tooltip, exact row highlight, headerless and reordered grids');
