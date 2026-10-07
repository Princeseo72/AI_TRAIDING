const G=require('./app/src/main/assets/runner-guard.js');
function assert(c,m){if(!c)throw new Error(m)}

const horses=[
  {number:1,name:'정상1',active:true},
  {number:2,name:'취소마',active:true},
  {number:3,name:'정상3',active:true},
  {number:4,name:'정상4',active:true}
];
const excluded=[{number:2,name:'취소마',excluded:true}];
const pools={
  WIN:{
    T20:[{key:'1',odds:2.1},{key:'2',odds:4.5},{key:'3',odds:6.2},{key:'4',odds:8.0}],
    T5:[{key:'1',odds:2.0},{key:'2',odds:4.0},{key:'3',odds:6.0},{key:'4',odds:7.5}]
  },
  QUINELLA:{
    T20:[{key:'1-2',odds:5.0},{key:'1-3',odds:6.0}],
    T5:[{key:'1-2',odds:4.8},{key:'1-3',odds:5.8}]
  }
};
const active=G.activeHorses(horses,excluded);
assert(active.map(x=>x.number).join(',')==='1,3,4','excluded horse must be removed even if active=true upstream');
const safe=G.sanitizePools(pools,active,excluded);
assert(safe.WIN.T20.map(x=>x.key).join(',')==='1,3,4','excluded WIN T20 row leaked');
assert(safe.WIN.T5.map(x=>x.key).join(',')==='1,3,4','excluded WIN T5 row leaked');
assert(safe.QUINELLA.T20.length===1&&safe.QUINELLA.T20[0].key==='1-3','excluded combo T20 leaked');
assert(safe.QUINELLA.T5.length===1&&safe.QUINELLA.T5[0].key==='1-3','excluded combo T5 leaked');
assert(G.validateNoExcludedLeak(safe,active,excluded).ok,'sanitized pools should pass');
assert(!G.validateNoExcludedLeak(pools,active,excluded).ok,'raw leaking pools must fail');
console.log('RUNNER GUARD TESTS PASSED');
