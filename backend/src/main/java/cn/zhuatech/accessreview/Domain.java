/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.accessreview;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.*;
import static cn.zhuatech.accessreview.Model.*;
import static cn.zhuatech.accessreview.Engine.*;
/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Component public class Domain {
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 static String text(Row r,String k){return txt(r.data(),k);}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 static List<Row> linked(Engine e,User u,String module,String field,String id){return e.all(u,module).stream().filter(x->text(x,field).equals(id)).toList();}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 static List<Row> inScope(Engine e,User u,String system){return e.all(u,"entitlements").stream().filter(x->x.state().equals("ACTIVE")&&(system.equalsIgnoreCase("ALL")||text(x,"system").equalsIgnoreCase(system))).toList();}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 static List<Row> missing(Engine e,User u,Row campaign){Set<String>covered=new HashSet<>();for(Row review:linked(e,u,"reviews","campaign",campaign.id()))covered.add(text(review,"entitlement"));return inScope(e,u,text(campaign,"system")).stream().filter(x->!covered.contains(x.id())).toList();}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 static void appendReviews(Engine e,User u,Row campaign,List<Row> items){for(Row ent:items)e.ledger(u,"reviews","PENDING",Map.of("campaign",campaign.id(),"entitlement",ent.id(),"identity",text(ent,"identity"),"system",text(ent,"system"),"privilege",text(ent,"privilege"),"risk",text(ent,"risk")));}
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public void create(Engine e,User u,String module,Map<String,Object>d){
  switch(module){
   case "identities" -> require(e.all(u,module).stream().noneMatch(x->text(x,"employeeNo").equalsIgnoreCase(txt(d,"employeeNo"))),"员工编号重复");
   case "entitlements" -> {e.ref(u,d,"identity","identities");require(!date(d,"grantedAt").isAfter(LocalDate.now()),"授权日期不能是未来");require(e.all(u,module).stream().noneMatch(x->x.state().equals("ACTIVE")&&text(x,"identity").equals(txt(d,"identity"))&&text(x,"system").equalsIgnoreCase(txt(d,"system"))&&text(x,"privilege").equalsIgnoreCase(txt(d,"privilege"))),"有效权限重复");}
   case "campaigns" -> require(!date(d,"dueDate").isBefore(LocalDate.now()),"截止日期不能早于今天");
  }
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public void edit(Engine e,User u,Row r,Map<String,Object>d){
  if(r.module().equals("identities"))require(e.all(u,"identities").stream().noneMatch(x->!x.id().equals(r.id())&&text(x,"employeeNo").equalsIgnoreCase(txt(d,"employeeNo"))),"员工编号重复");
 }
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public String action(Engine e,User u,Row r,String action,Map<String,Object>i,Map<String,Object>d){
  switch(r.module()+"."+action){
   case "campaigns.launch" -> {
    String system=txt(d,"system");var active=inScope(e,u,system);
    require(!active.isEmpty(),"当前范围没有有效权限");
    require(e.all(u,"campaigns").stream().noneMatch(x->!x.id().equals(r.id())&&x.state().equals("ACTIVE")&&(system.equalsIgnoreCase("ALL")||text(x,"system").equalsIgnoreCase("ALL")||text(x,"system").equalsIgnoreCase(system))),"同一系统已有进行中的复核活动");
    appendReviews(e,u,r,active);
    d.put("scopeCount",active.size());d.put("launchedAt",Instant.now().toString());
   }
   case "campaigns.sync" -> {
    var additions=missing(e,u,r);require(!additions.isEmpty(),"当前范围没有待补录的新增授权");
    appendReviews(e,u,r,additions);d.put("scopeCount",((Number)d.get("scopeCount")).intValue()+additions.size());d.put("lastSyncedAt",Instant.now().toString());d.put("lastSyncCount",additions.size());
   }
   case "campaigns.close" -> {
    require(missing(e,u,r).isEmpty(),"活动期间有新增授权尚未补录，请先补录并完成复核");
    var items=linked(e,u,"reviews","campaign",r.id());require(!items.isEmpty()&&items.size()==((Number)d.get("scopeCount")).intValue(),"复核范围不完整");
    require(items.stream().allMatch(x->Set.of("CERTIFIED","REMEDIATED").contains(x.state())),"仍有待决策或待整改权限");
    d.put("closedAt",Instant.now().toString());d.put("closedBy",u.username());
   }
   case "reviews.certify","reviews.revoke" -> {
    Row campaign=e.ref(u,d,"campaign","campaigns");require(campaign.state().equals("ACTIVE"),"活动已关闭");
    d.put("decisionBy",u.username());d.put("decisionAt",Instant.now().toString());d.put("decisionReason",txt(i,"reason"));
    if(action.equals("certify")&&txt(d,"risk").equals("HIGH"))return "CERTIFICATION_PENDING";
   }
   case "reviews.confirm","reviews.deny" -> {
    Row campaign=e.ref(u,d,"campaign","campaigns");require(campaign.state().equals("ACTIVE"),"活动已关闭");
    require(txt(d,"risk").equals("HIGH"),"仅高风险保留决策需要二次确认");
    require(!u.username().equals(txt(d,"decisionBy")),"初审与二次确认必须由不同人员完成");
    String reason=txt(i,"reason");
    boolean approved=action.equals("confirm");
    e.ledger(u,"confirmations","POSTED",Map.of("review",r.id(),"decisionBy",txt(d,"decisionBy"),"confirmedBy",u.username(),"reason",reason,"confirmedAt",Instant.now().toString(),"decision",approved?"APPROVED":"DENIED"));
    d.put("confirmedBy",u.username());d.put("confirmedAt",Instant.now().toString());d.put("confirmationReason",reason);d.put("confirmationDecision",approved?"APPROVED":"DENIED");
    if(!approved)return "REVOKE_REQUESTED";
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
 /**
  * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
  */
 public Map<String,Object> metrics(Engine e,User u){return Map.of("待复核权限",e.all(u,"reviews").stream().filter(r->Set.of("PENDING","CERTIFICATION_PENDING").contains(r.state())).count(),"待撤权整改",e.all(u,"reviews").stream().filter(r->r.state().equals("REVOKE_REQUESTED")).count(),"已关闭活动",e.all(u,"campaigns").stream().filter(r->r.state().equals("CLOSED")).count());}
}
