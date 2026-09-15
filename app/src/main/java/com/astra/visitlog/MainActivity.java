package com.astra.visitlog;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public final class XlsxNative {
    private XlsxNative() {}

    public static byte[] buildWorkbook(JSONObject data) throws Exception {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<List<List<String>>> sheets = new ArrayList<>();

        sheets.add(summary(data)); names.add("Summary");
        sheets.add(clientMaster(data)); names.add("Client Master List");
        sheets.add(cityLog(data, "Lahore", data.optJSONObject("settings").optInt("planL",20))); names.add("Lahore");
        sheets.add(cityLog(data, "Islamabad", data.optJSONObject("settings").optInt("planI",10))); names.add("Islamabad");
        List<List<String>> backup = new ArrayList<>();
        backup.add(Arrays.asList("ASTRA Visit Log full backup (do not edit this sheet)"));
        JSONObject payload = new JSONObject(); payload.put("format","ASTRA Visit Log Backup"); payload.put("version",1); payload.put("exportedAt",new Date().toString()); payload.put("data",data);
        backup.add(Arrays.asList(payload.toString())); names.add("ASTRA Backup"); sheets.add(backup);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        put(zip,"[Content_Types].xml",contentTypes(names.size()));
        put(zip,"_rels/.rels",rootRels());
        put(zip,"xl/workbook.xml",workbook(names));
        put(zip,"xl/_rels/workbook.xml.rels",workbookRels(names.size()));
        for(int i=0;i<sheets.size();i++) put(zip,"xl/worksheets/sheet"+(i+1)+".xml",sheetXml(sheets.get(i)));
        zip.finish(); zip.close(); return out.toByteArray();
    }

    private static List<List<String>> summary(JSONObject d){
        List<List<String>> r=new ArrayList<>(); r.add(Arrays.asList("ASTRA Visit Log — Travel & Expense Summary"));
        r.add(Arrays.asList("City","Planned Days","Days Logged","Total Distance (KM)","Total Expense (Rs.)"));
        JSONArray v=d.optJSONArray("visits"); int l=0,is=0; double lkm=0,ikm=0,le=0,ie=0;
        for(int i=0;i<(v==null?0:v.length());i++){JSONObject x=v.optJSONObject(i); if(x==null)continue; String c=x.optString("city"); double km=x.optDouble("km",0); double e=x.optDouble("fuel",0)+x.optDouble("food",0)+x.optDouble("toll",0)+x.optDouble("other",0); if("Islamabad".equals(c)){is++;ikm+=km;ie+=e;}else{l++;lkm+=km;le+=e;}}
        JSONObject s=d.optJSONObject("settings"); int pl=s==null?20:s.optInt("planL",20), pi=s==null?10:s.optInt("planI",10);
        r.add(Arrays.asList("Lahore",String.valueOf(pl),String.valueOf(l),fmt(lkm),fmt(le))); r.add(Arrays.asList("Islamabad",String.valueOf(pi),String.valueOf(is),fmt(ikm),fmt(ie))); r.add(Arrays.asList("Total",String.valueOf(pl+pi),String.valueOf(l+is),fmt(lkm+ikm),fmt(le+ie))); return r;
    }
    private static List<List<String>> clientMaster(JSONObject d){
        List<List<String>> r=new ArrayList<>(); r.add(Arrays.asList("ASTRA Visit Log — Client Master List"));
        r.add(Arrays.asList("Client Name","City","Focal Person Name","Designation","Contact #","Full Address","Google Maps Link","Distance (KM, one-way)"));
        JSONArray a=d.optJSONArray("clients"); for(int i=0;i<(a==null?0:a.length());i++){JSONObject c=a.optJSONObject(i); if(c==null)continue; r.add(Arrays.asList(c.optString("name"),c.optString("city"),c.optString("person"),c.optString("designation"),c.optString("contact"),c.optString("address"),c.optString("map"),fmt(c.optDouble("distance",0))));} return r;
    }
    private static List<List<String>> cityLog(JSONObject d,String city,int planned){
        List<List<String>> r=new ArrayList<>(); r.add(Arrays.asList("ASTRA Visit Log — Travel & Expense Log ("+city+")"));
        r.add(Arrays.asList("Date","Day #","Client 1","Dist. (KM)","Client 2","Dist. (KM)","Client 3","Dist. (KM)","Client 4","Dist. (KM)","Google Maps Route","Total Distance (KM)","Fuel (Rs.)","Fuel Basis","Fuel Rate (Rs/KM)","Food (Rs.)","Food Basis","Meals","Meal Rate (Rs.)","Nights","Night Rate (Rs.)","Toll (Rs.)","Other (Rs.)","Total Expense (Rs.)","Remarks / Status"));
        JSONArray va=d.optJSONArray("visits"); ArrayList<JSONObject> list=new ArrayList<>(); for(int i=0;i<(va==null?0:va.length());i++){JSONObject x=va.optJSONObject(i);if(x!=null&&city.equals(x.optString("city")))list.add(x);} Collections.sort(list,(a,b)->a.optString("date").compareTo(b.optString("date")));
        JSONArray ca=d.optJSONArray("clients");
        for(int day=1;day<=Math.max(planned,list.size());day++){if(day>list.size()){r.add(Arrays.asList("",String.valueOf(day)));continue;} JSONObject v=list.get(day-1); ArrayList<String> row=new ArrayList<>(); row.add(v.optString("date"));row.add(String.valueOf(day)); JSONArray ids=v.optJSONArray("clientIds"); for(int j=0;j<4;j++){String nm="",dist=""; if(ids!=null&&j<ids.length()){String id=ids.optString(j); JSONObject c=findClient(ca,id); if(c!=null){nm=c.optString("name");dist=fmt(c.optDouble("distance",0));}} row.add(nm);row.add(dist);} row.add(v.optString("route"));row.add(fmt(v.optDouble("km",0)));row.add(fmt(v.optDouble("fuel",0)));row.add("calc".equals(v.optString("fuelMode"))?"Calculated":"Actual");row.add(fmt(v.optDouble("fuelRate",0)));row.add(fmt(v.optDouble("food",0)));row.add("calc".equals(v.optString("foodMode"))?"Calculated":"Actual");row.add(String.valueOf(v.optInt("mealCount",0)));row.add(fmt(v.optDouble("mealRate",0)));row.add(String.valueOf(v.optInt("nightCount",0)));row.add(fmt(v.optDouble("nightRate",0)));row.add(fmt(v.optDouble("toll",0)));row.add(fmt(v.optDouble("other",0)));row.add(fmt(v.optDouble("fuel",0)+v.optDouble("food",0)+v.optDouble("toll",0)+v.optDouble("other",0)));row.add(v.optString("remarks"));r.add(row);} return r;
    }
    private static JSONObject findClient(JSONArray a,String id){if(a==null)return null;for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(c!=null&&String.valueOf(c.opt("id")).equals(id))return c;}return null;}
    private static String fmt(double x){if(Math.rint(x)==x)return String.valueOf((long)x);return String.format(Locale.US,"%.1f",x);}
    private static void put(ZipOutputStream z,String name,String s)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(s.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String esc(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String col(int n){String s="";while(n>0){n--;s=(char)('A'+n%26)+s;n/=26;}return s;}
    private static String sheetXml(List<List<String>> rows){StringBuilder b=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");for(int i=0;i<rows.size();i++){b.append("<row r=\"").append(i+1).append("\">");List<String> row=rows.get(i);for(int j=0;j<row.size();j++){String v=row.get(j)==null?"":row.get(j);String ref=col(j+1)+(i+1);if(v.matches("-?\\d+(\\.\\d+)?"))b.append("<c r=\"").append(ref).append("\"><v>").append(esc(v)).append("</v></c>");else b.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(v)).append("</t></is></c>");}b.append("</row>");}return b.append("</sheetData></worksheet>").toString();}
    private static String contentTypes(int n){StringBuilder b=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");for(int i=1;i<=n;i++)b.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");return b.append("</Types>").toString();}
    private static String rootRels(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";}
    private static String workbook(List<String> names){StringBuilder b=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");for(int i=0;i<names.size();i++)b.append("<sheet name=\"").append(esc(names.get(i))).append("\" sheetId=\"").append(i+1).append("\" r:id=\"rId").append(i+1).append("\"/>");return b.append("</sheets></workbook>").toString();}
    private static String workbookRels(int n){StringBuilder b=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");for(int i=1;i<=n;i++)b.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");return b.append("</Relationships>").toString();}

    public static JSONObject readWorkbook(byte[] bytes) throws Exception {
        HashMap<String,byte[]> entries=new HashMap<>(); ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(bytes)); ZipEntry e; while((e=zin.getNextEntry())!=null){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=zin.read(buf))!=-1)b.write(buf,0,n);entries.put(e.getName(),b.toByteArray());}zin.close();
        ArrayList<String> shared=new ArrayList<>(); if(entries.containsKey("xl/sharedStrings.xml")){Document d=xml(entries.get("xl/sharedStrings.xml"));NodeList ts=d.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main","t");for(int i=0;i<ts.getLength();i++)shared.add(ts.item(i).getTextContent());}
        Document wb=xml(entries.get("xl/workbook.xml")); Document rel=xml(entries.get("xl/_rels/workbook.xml.rels")); HashMap<String,String> ridTarget=new HashMap<>(); NodeList rels=rel.getDocumentElement().getChildNodes();for(int i=0;i<rels.getLength();i++){Node n=rels.item(i);if(n.getNodeType()==Node.ELEMENT_NODE)ridTarget.put(attr(n,"Id"),attr(n,"Target"));}
        JSONObject result=new JSONObject(); JSONObject sheets=new JSONObject(); NodeList ss=wb.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main","sheet");for(int i=0;i<ss.getLength();i++){Node s=ss.item(i);String name=attr(s,"name"),rid=attr(s,"r:id");String target=ridTarget.get(rid);if(target==null)continue;if(!target.startsWith("/"))target="xl/"+target; if(!target.startsWith("xl/"))target="xl/"+target; byte[] x=entries.get(target);if(x==null)continue;sheets.put(name,readSheet(x,shared));}result.put("sheets",sheets); return result;
    }
    private static JSONArray readSheet(byte[] xml,ArrayList<String> shared)throws Exception{Document d=xml(xml);NodeList cs=d.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main","c");HashMap<Integer,HashMap<Integer,String>> map=new HashMap<>();int maxR=0,maxC=0;for(int i=0;i<cs.getLength();i++){Node c=cs.item(i);String ref=attr(c,"r"),type=attr(c,"t");int[] rc=rc(ref);int r=rc[0],col=rc[1];maxR=Math.max(maxR,r);maxC=Math.max(maxC,col);String val="";if("inlineStr".equals(type)){NodeList ts=((Element)c).getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main","t");if(ts.getLength()>0)val=ts.item(0).getTextContent();}else{NodeList vs=((Element)c).getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main","v");if(vs.getLength()>0){val=vs.item(0).getTextContent();if("s".equals(type)){try{val=shared.get(Integer.parseInt(val));}catch(Exception ignored){}}}}map.computeIfAbsent(r,k->new HashMap<>()).put(col,val);}JSONArray out=new JSONArray();for(int r=1;r<=maxR;r++){JSONArray row=new JSONArray();HashMap<Integer,String> rm=map.get(r);for(int c=1;c<=maxC;c++)row.put(rm==null?"":rm.getOrDefault(c,""));out.put(row);}return out;}
    private static int[] rc(String ref){int i=0;while(i<ref.length()&&Character.isLetter(ref.charAt(i)))i++;String letters=ref.substring(0,i).toUpperCase(Locale.US);int c=0;for(char ch:letters.toCharArray())c=c*26+(ch-'A'+1);return new int[]{Integer.parseInt(ref.substring(i)),c};}
    private static String attr(Node n,String a){String v=n.getAttributes()!=null&&n.getAttributes().getNamedItem(a)!=null?n.getAttributes().getNamedItem(a).getNodeValue():"";if(v.isEmpty()&&a.equals("r:id")&&n.getAttributes()!=null){NodeList x=n.getAttributes().getLength()>0?null:null;for(int i=0;i<n.getAttributes().getLength();i++){Node q=n.getAttributes().item(i);if(q.getNodeName().endsWith(":id")){v=q.getNodeValue();break;}}}return v;}
    private static Document xml(byte[] b)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);return f.newDocumentBuilder().parse(new ByteArrayInputStream(b));}
}
