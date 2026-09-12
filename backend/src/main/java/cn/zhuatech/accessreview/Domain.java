/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.accessreview;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.*;
import static cn.zhuatech.accessreview.Model.*;
import static cn.zhuatech.accessreview.Engine.*;
@Component public class Domain {
 static String text(Row r,String k){return txt(r.data(),k);}
 static List<Row> linked(Engine e,User u,String module,String field,String id){return e.all(u,module).stream().filter(x->text(x,field).equals(id)).toList();}
 public void create(Engine e,User u,String module,Map<String,Object>d){
  switch(module){
   case "identities" -> require(e.all(u,module).stream().noneMatch(x->text(x,"employeeNo").equalsIgnoreCase(txt(d,"employeeNo"))),"员工编号重复");
   case "entitlements" -> {e.ref(u,d,"identity","identities");require(!date(d,"grantedAt").isAfter(LocalDate.now()),"授权日期不能是未来");require(e.all(u,module).stream().noneMatch(x->x.state().equals("ACTIVE")&&text(x,"identity").equals(txt(d,"identity"))&&text(x,"system").equalsIgnoreCase(txt(d,"system"))&&text(x,"privilege").equalsIgnoreCase(txt(d,"privilege"))),"有效权限重复");}
   case "campaigns" -> require(!date(d,"dueDate").isBefore(LocalDate.now()),"截止日期不能早于今天");
  }
 }
 public void edit(Engine e,User u,Row r,Map<String,Object>d){
  if(r.module().equals("identities"))require(e.all(u,"identities").stream().noneMatch(x->!x.id().equals(r.id())&&text(x,"employeeNo").equalsIgnoreCase(txt(d,"employeeNo"))),"员工编号重复");
 }
 public String action(Engine e,User u,Row r,String action,Map<String,Object>i,Map<String,Object>d){
  switch(r.module()+"."+action){
   case "campaigns.launch" -> {
    String system=txt(d,"system");var active=e.all(u,"entitlements").stream().filter(x->x.state().equals("ACTIVE")&&(system.equalsIgnoreCase("ALL")||text(x,"system").equalsIgnoreCase(system))).toList();
    require(!active.isEmpty(),"当前范围没有有效权限");
    require(e.all(u,"campaigns").stream().noneMatch(x->!x.id().equals(r.id())&&x.state().equals("ACTIVE")&&text(x,"system").equalsIgnoreCase(system)),"同一系统已有进行中的复核活动");
    for(Row ent:active)e.ledger(u,"reviews","PENDING",Map.of("campaign",r.id(),"entitlement",ent.id(),"identity",text(ent,"identity"),"system",text(ent,"system"),"privilege",text(ent,"privilege"),"risk",text(ent,"risk")));
    d.put("scopeCount",active.size());d.put("launchedAt",Instant.now().toString());
   }
   case "campaigns.close" -> {
    var items=linked(e,u,"reviews","campaign",r.id());require(!items.isEmpty()&&items.size()==((Number)d.get("scopeCount")).intValue(),"复核范围不完整");
    require(items.stream().allMatch(x->Set.of("CERTIFIED","REMEDIATED").contains(x.state())),"仍有待决策或待整改权限");
    d.put("closedAt",Instant.now().toString());d.put("closedBy",u.username());
   }
   case "reviews.certify","reviews.revoke" -> {
    Row campaign=e.ref(u,d,"campaign","campaigns");require(campaign.state().equals("ACTIVE"),"活动已关闭");
    d.put("decisionBy",u.username());d.put("decisionAt",Instant.now().toString());d.put("decisionReason",txt(i,"reason"));
   }
   case "reviews.remediate" -> {
    Row ent=e.ref(u,d,"entitlement","entitlements");require(ent.state().equals("ACTIVE"),"权限已撤销");
    require(e.all(u,"remediations").stream().noneMatch(x->text(x,"ticket").equalsIgnoreCase(txt(i,"ticket"))),"整改工单号重复");
    e.save(u,ent,"REVOKED",new LinkedHashMap<>(ent.data()),"REVOKE","权限复核整改："+txt(i,"ticket"));
    e.ledger(u,"remediations","POSTED",Map.of("review",r.id(),"entitlement",ent.id(),"ticket",txt(i,"ticket"),"executor",u.username()));
    d.put("remediatedAt",Instant.now().toString());d.put("ticket",txt(i,"ticket"));
   }
  }
  return null;
 }
 public Map<String,Object> metrics(Engine e,User u){return Map.of("待复核权限",e.all(u,"reviews").stream().filter(r->r.state().equals("PENDING")).count(),"待撤权整改",e.all(u,"reviews").stream().filter(r->r.state().equals("REVOKE_REQUESTED")).count(),"已关闭活动",e.all(u,"campaigns").stream().filter(r->r.state().equals("CLOSED")).count());}
}
