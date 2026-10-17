# derived percentiles: CSEP bands -> percentiles; normal approx from mean/SD; load-model conversions
Z={5:-1.645,20:-0.8416,50:0,80:0.8416,95:1.645}
def normal_pcts(m,sd): return {f"p{p}":round(max(0,m+z*sd),1) for p,z in Z.items()}
csep_m={"15-19":(17,22,28,38),"20-29":(16,21,28,35),"30-39":(11,16,21,29),"40-49":(9,12,16,24),"50-59":(6,9,12,20),"60-69":(4,7,10,17)}
csep_f={"15-19":(11,17,24,32),"20-29":(9,14,20,29),"30-39":(7,12,19,26),"40-49":(4,10,14,23),"50-59":(1,6,10,20),"60-69":(1,4,11,16)}
# tuple = upper bound of NI, Fair, Good, Very good; boundaries assumed at P20,P40,P60,P80
def csep_pcts(t):
    p20=t[0]+.5; p40=t[1]+.5; p60=t[2]+.5; p80=t[3]+.5
    p50=(p40+p60)/2; sd=(p80-p20)/1.6832
    return {"p5":round(max(0,p50-1.645*sd),1),"p20":p20,"p50":p50,"p80":p80,"p95":round(p50+1.645*sd,1)}
out={}
out["csep_men_standard"]={k:csep_pcts(v) for k,v in csep_m.items()}
out["csep_women_knee"]={k:csep_pcts(v) for k,v in csep_f.items()}
# load model (Epley): rel1RM = load*(1+n/30); n_target = 30*(rel1RM/load_target - 1)
def conv(n,src,dst,dst_scale=1.0):
    r=src*(1+n/30.0)*dst_scale
    return round(max(0.0,30*(r/dst-1)),1)
# women knee (0.49 BW) -> standard push-up (0.64 BW)
out["csep_women_standard_est"]={k:{p:conv(v,0.49,0.64) for p,v in d.items()} for k,d in out["csep_women_knee"].items()}
# push-ups (0.64) -> dips (0.95)
out["dips_from_csep_men"]={k:{p:conv(v,0.64,0.95) for p,v in d.items()} for k,d in out["csep_men_standard"].items()}
out["dips_from_csep_women_std_est"]={k:{p:conv(v,0.64,0.95) for p,v in d.items()} for k,d in out["csep_women_standard_est"].items()}
# push-ups (0.64) -> HSPU: moved load ~0.92 BW, overhead strength ~0.63 of horizontal press => equivalent 1.46
out["hspu_from_csep_men"]={k:{p:conv(v,0.64,0.92/0.63) for p,v in d.items()} for k,d in out["csep_men_standard"].items()}
# marines
out["usmc_male_pullups_normal"]=normal_pcts(18.0,5.0)
out["usmc_female_pullups_normal"]=normal_pcts(7.1,4.3)
# pull-ups (0.95 BW) -> inverted row (~0.65 BW, body near horizontal, feet on floor)
for lab,d in (("male",out["usmc_male_pullups_normal"]),("female",out["usmc_female_pullups_normal"])):
    out[f"inverted_row_from_usmc_{lab}"]={p:conv(v,0.95,0.65) for p,v in d.items()}
# chin-ups ~ x1.10
for lab in ("male","female"):
    out[f"chinups_from_usmc_{lab}"]={p:round(v*1.10,1) for p,v in out[f"usmc_{lab}_pullups_normal"].items()}
import json; print(json.dumps(out,indent=1))
