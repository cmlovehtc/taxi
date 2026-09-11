// Export rules/content and independent parity fixtures from the published Site source.
import fs from 'node:fs';
import {createHash} from 'node:crypto';
import path from 'node:path';
import { registerHooks, stripTypeScriptTypes } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
const web = path.resolve(process.argv[2]);
const out = path.resolve(import.meta.dirname, '..');
registerHooks({
 resolve(spec,ctx,next){if(spec.startsWith('@/')){let p=path.join(web,spec.slice(2));if(!path.extname(p))p+='.ts';return{url:pathToFileURL(p).href,shortCircuit:true};}return next(spec,ctx);},
 load(url,ctx,next){if(url.startsWith(pathToFileURL(web).href)&&url.endsWith('.json'))return {format:'module',source:'export default '+fs.readFileSync(fileURLToPath(url),'utf8'),shortCircuit:true};
 if(url.startsWith(pathToFileURL(web).href)&&url.endsWith('.ts')){let source=fs.readFileSync(fileURLToPath(url),'utf8');if(url.endsWith('/keelung-memory.ts'))source+='\nexport { KEELUNG_MEMORY_MATCHERS };';if(url.endsWith('/traffic-law-categories.ts'))source+='\nexport { CATEGORY_KEYWORDS, CATEGORY_PRIORITY };';return {format:'module',source:stripTypeScriptTypes(source),shortCircuit:true};}return next(url,ctx);}
});
const imp=p=>import(pathToFileURL(path.join(web,p)).href);
const qb=await imp('lib/question-bank.ts'), prof=await imp('lib/exam-region-profiles.ts'), memory=await imp('lib/regional-memory.ts'), keelung=await imp('lib/keelung-memory.ts'), traffic=await imp('lib/traffic-law-categories.ts'), exp=await imp('lib/question-explanations.ts');
const bundle=JSON.parse(fs.readFileSync(path.join(web,'lib/question-bank-bundle.json')));
const guides={};for(const region of memory.MEMORY_GUIDE_REGIONS){guides[region]=region==='基隆市'?{region,title:'基隆地理最佳記憶法',summary:'不逐題背句子；先建立港區路網，再把機關綁成群、把郊區綁成走廊，最後只練方向與相似地址的反向題。',masterMnemonic:'信奇回港、信偶離港；仁四往小、仁五往大；義六雙，其餘二三四五七單。',chapters:keelung.KEELUNG_MEMORY_CHAPTERS.map(c=>({...c,matcher:keelung.KEELUNG_MEMORY_MATCHERS[c.id]}))}:memory.getRegionalMemoryProfile(region);}
// Extract the full fixed Keelung study sections (including tables) from source, not rewritten summaries.
const tsx=fs.readFileSync(path.join(web,'components/keelung-memory-guide.tsx'),'utf8');
const constants={};for(const m of tsx.matchAll(/const (\w+) = (\[[\s\S]*?\]) as const;/g))constants[m[1]]=Function('return '+m[2])();
const clean=s=>s.replace(/<[^>]+>/g,'').replace(/\{\/\*[\s\S]*?\*\/\}/g,'').replace(/\s+/g,' ').trim();
guides['基隆市'].sections=[];
for(const match of tsx.matchAll(/<AccordionItem value="([^"]+)">([\s\S]*?)<\/AccordionItem>/g)){
 const b=match[2],title=clean(b.match(/<AccordionTrigger[^>]*>([\s\S]*?)<\/AccordionTrigger>/)[1]);const blocks=[];
 const pattern=/<MemoryTable headers=\{(\[[^}]*\])\} rows=\{(\w+)\} \/>|<(MemoryCue|h5|p|li)(?:\s[^>]*?)?>([\s\S]*?)<\/\3>|\{(TURN_RULES|XIN_TWO_CLUSTER|NUMBER_CARDS)\.map/g;
 for(const m of b.matchAll(pattern)){if(m[1])blocks.push({headers:Function('return '+m[1])(),rows:constants[m[2]]});else if(m[5])blocks.push({rows:constants[m[5]].map(v=>Array.isArray(v)?v:[v])});else {const text=clean(m[4]);if(text&&!text.includes('{'))blocks.push({text,bold:m[3]==='MemoryCue'||m[3]==='h5'});}}
 for(const m of b.matchAll(/\{(\[[\s\S]*?\])\.map\(\(\[step, title, description\]\)/g))blocks.unshift({rows:Function('total','return '+m[1])(bundle.bank['基隆市'].是非題.length+bundle.bank['基隆市'].選擇題.length)});
 for(const m of b.matchAll(/<span><strong[^>]*>判定是否真的記住：[\s\S]*?<\/span>/g))blocks.push({text:clean(m[0]),bold:true});
 guides['基隆市'].sections.push({title,blocks});
}
const data={schema:1,site:'https://taxi-exam-tw.cmlove.chatgpt.site/',sourceCommit:'88cbb0e9c0a290f5f889a173335047556c2d5b98',sourceVersion:71,manifest:bundle.manifest,regions:qb.REGIONS,registrationRegions:[...prof.NORTHERN_REGISTRATION_REGIONS,...prof.OTHER_REGISTRATION_REGIONS],profiles:prof.EXAM_REGION_PROFILES,guides,trafficCategories:traffic.TRAFFIC_LAW_CATEGORIES,trafficKeywords:traffic.CATEGORY_KEYWORDS,trafficPriority:traffic.CATEGORY_PRIORITY,reviewed:JSON.parse(fs.readFileSync(path.join(web,'lib/reviewed-question-notes.json')))};
fs.writeFileSync(path.join(out,'assets/study-data.json'),JSON.stringify(data,(_,v)=>v instanceof RegExp?v.source:v));
// Full-source oracle: native explanations, classification, options and evidence must match.
const expected={};for(const [region,types] of Object.entries(bundle.bank)){const all=[...types.是非題,...types.選擇題];for(const q of all){expected[q.id]={q,chapters:memory.isMemoryGuideRegion(region)?memory.getMemoryChapterIds(region,q):region==='交通法令'?[traffic.classifyTrafficLawQuestion(q)]:[],explanation:exp.buildQuestionExplanation(q,all,true)};}}
function canonical(v){if(Array.isArray(v))return '['+v.map(canonical).join('')+']';if(v&&typeof v==='object')return '{'+Object.keys(v).sort().map(k=>canonical(k)+canonical(v[k])).join('')+'}';let s=String(v);return s.length+':'+s;}
const oracle=Object.fromEntries(Object.entries(expected).map(([k,v])=>[k,createHash('sha256').update(canonical(v)).digest('hex')]));
fs.writeFileSync(path.join(out,'tests/web-parity.json'),JSON.stringify(oracle));
// Java registration profiles are generated verbatim from the same source for pure-Java sampling tests.
const str=JSON.stringify;
const java=`package tw.cmlove.taxiexam;\nimport java.util.*;\n/** Generated from published Site v71; run tools/export-web-data.mjs to refresh. */\npublic final class ExamProfiles {\n public static final String[] REGIONS=${'{'+qb.REGIONS.map(str).join(',')+'}'};\n public static final String[] REGISTRATION_REGIONS=${'{'+data.registrationRegions.map(str).join(',')+'}'};\n public static Map<String,Integer> ratios(String city){Map<String,Integer> r=new LinkedHashMap<>();switch(city){\n${Object.entries(prof.EXAM_REGION_PROFILES).map(([city,p])=>'case '+str(city)+': '+Object.entries(p.ratios).map(([name,n])=>'r.put('+str(name)+','+n+');').join('')+' break;').join('\n')}\n default:throw new IllegalArgumentException("請選擇報考縣市");}return r;}\n}\n`;
fs.writeFileSync(path.join(out,'src/tw/cmlove/taxiexam/ExamProfiles.java'),java);
console.log(JSON.stringify({questions:Object.keys(expected).length,profiles:Object.keys(data.profiles).length,guides:Object.keys(guides),assetBytes:fs.statSync(path.join(out,'assets/study-data.json')).size,oracleBytes:fs.statSync(path.join(out,'tests/web-parity.json')).size}));
