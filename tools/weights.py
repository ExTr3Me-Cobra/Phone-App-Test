# How hard each secondary muscle GROUP works compared with the main group (main = 1.0).
# Rules are checked in order; the first rule that matches the exercise name (and primary
# muscle, if given) and lists the muscle sets its weight. Otherwise DEFAULT applies.
# Numbers are rounded estimates from EMG studies (peak or mean %MVC of the helper muscle divided by
# that of the main muscle) — sources per rule, full references in detail_weights.REFS.
# How each group splits into heads is in detail_weights.py.
RULES = [
    # Pressing
    (r'close-?grip.*(bench|press)|jm press|tate press|board press|pin press', 'triceps', {'chest': .6, 'shoulders': .45},
     ['Barnett 1995', 'Stastny 2017']),
    (r'incline.*(press|bench)', None, {'shoulders': .7, 'triceps': .45, 'serratus': .25},
     ['Trebs 2010', 'Rodriguez-Ridao 2020', 'Decker 1999']),
    (r'decline.*(press|bench)', None, {'triceps': .5, 'shoulders': .3}, ['Glass 1997', 'Barnett 1995']),
    (r'bench press|chest press|floor press|smith.*press|machine bench', None, {'triceps': .5, 'shoulders': .6, 'serratus': .15},
     ['Barnett 1995', 'Stastny 2017', 'Decker 1999']),
    (r'push-?ups?\b|push up', None, {'triceps': .5, 'shoulders': .5, 'serratus': .45, 'abdominals': .25, 'chest': .6},
     ['Calatayud 2015', 'Ludewig 2004', 'Decker 1999']),
    (r'dips?\b', None, {'chest': .6, 'triceps': .65, 'shoulders': .45}, ['Barnett 1995', 'Stastny 2017']),
    (r'fl(y|ye|yes)\b|crossover|pec deck|butterfly|iron cross', 'chest', {'shoulders': .35, 'biceps': .1}, ['Campos 2020', 'Neumann 2017']),
    (r'pullover', None, {'lats': .6, 'chest': .6, 'serratus': .4, 'triceps': .3}, ['Marchetti 2011']),
    (r'shoulder press|military press|overhead press|push press|arnold|jerk|landmine press|bradford', None,
     {'triceps': .55, 'traps': .35, 'serratus': .35, 'chest': .2, 'abdominals': .15, 'rotator cuff': .3},
     ['Saeterbakken 2013', 'Ekstrom 2003', 'Reinold 2009']),
    (r'lateral raise|side laterals|deltoid raise|power partials', None, {'traps': .4, 'rotator cuff': .45},
     ['Coratella 2020', 'Ekstrom 2003', 'Reinold 2009']),
    (r'front .*raise|raise over head|raise above head', None, {'chest': .3, 'traps': .3, 'serratus': .3},
     ['Coratella 2020', 'Ekstrom 2003']),
    (r'upright row', None, {'traps': .7, 'biceps': .3, 'forearms': .2}, ['McAllister 2013']),
    (r'rear delt|reverse fl|face pull|band pull apart|y-t-w', None, {'middle back': .55, 'traps': .45, 'rotator cuff': .5, 'shoulders': .6},
     ['Ekstrom 2003', 'Reinold 2004', 'Campos 2020']),
    (r'external rotation|internal rotation|cuban', None, {'shoulders': .3}, ['Reinold 2004', 'Decker 2003']),
    # Pulling
    (r'pull-?ups?|chin-?ups?|muscle up|towel pull', None,
     {'biceps': .55, 'middle back': .5, 'traps': .45, 'forearms': .3, 'grip': .4, 'shoulders': .25, 'abdominals': .2},
     ['Youdas 2010', 'Lusk 2010']),
    # Neutral (palms-in) grip shifts elbow flexion work toward brachialis / brachioradialis.
    (r'neutral.*pulldown', None, {'biceps': .45, 'middle back': .45, 'traps': .35, 'forearms': .35, 'grip': .25, 'shoulders': .25},
     ['Lusk 2010', 'Andersen 2014', 'Kleiber 2015', 'Boland 2008']),
    (r'medium-grip lat pulldown', None, {'biceps': .45, 'middle back': .45, 'traps': .35, 'forearms': .25, 'grip': .25, 'shoulders': .25},
     ['Lusk 2010', 'Andersen 2014', 'Youdas 2010']),
    (r'pulldown', None, {'biceps': .45, 'middle back': .45, 'traps': .35, 'forearms': .25, 'grip': .25, 'shoulders': .25},
     ['Lusk 2010', 'Youdas 2010']),
    (r'(?<!upright )row\b|rows\b', None,
     {'middle back': .7, 'lats': .65, 'biceps': .4, 'shoulders': .45, 'traps': .45, 'forearms': .25, 'grip': .3, 'lower back': .25, 'rotator cuff': .3},
     ['Fenwick 2009', 'Ekstrom 2003', 'Reinold 2009']),
    (r'shrug', None, {'forearms': .2, 'grip': .35, 'middle back': .2}, ['Ekstrom 2003', 'Mogk 2003']),
    # Arms
    (r'hammer|reverse curl|zottman|reverse.*preacher|reverse plate', None, {'forearms': .6, 'grip': .3},
     ['Boland 2008', 'Kleiber 2015']),
    (r'curl', 'biceps', {'forearms': .35, 'grip': .15}, ['Kleiber 2015', 'Boland 2008']),
    (r'kickback|skull|triceps extension|tricep extension|pushdown|overhead triceps|triceps press', 'triceps',
     {'forearms': .1, 'shoulders': .15, 'chest': .15}, ['Neumann 2017']),
    (r'wrist', None, {'grip': .4}, ['Mogk 2003']),
    # Legs
    (r'front.*squat|goblet|zercher', None, {'glutes': .45, 'adductors': .4, 'abdominals': .3, 'lower back': .3, 'hamstrings': .2, 'calves': .1},
     ['Escamilla 2001', 'Kubo 2019', 'Hamlyn 2007']),
    (r'squat', None, {'glutes': .55, 'adductors': .45, 'hamstrings': .25, 'lower back': .35, 'calves': .15, 'abdominals': .2, 'quadriceps': .7},
     ['Caterisano 2002', 'Contreras 2015', 'Escamilla 2001', 'Kubo 2019', 'Hamlyn 2007']),
    (r'lunge|split squat|step-?up|step down', None, {'glutes': .6, 'hamstrings': .3, 'adductors': .35, 'calves': .2, 'quadriceps': .7},
     ['Boren 2011', 'Escamilla 2001']),
    (r'leg press|hack squat', None, {'glutes': .45, 'adductors': .35, 'hamstrings': .2, 'calves': .1}, ['Escamilla 1998', 'Escamilla 2001']),
    (r'stiff|romanian|good morning', None, {'glutes': .65, 'lower back': .55, 'hamstrings': .8, 'forearms': .2, 'grip': .3, 'traps': .2},
     ['McAllister 2014', 'Ono 2011', 'Escamilla 2002']),
    (r'deadlift|rack pull', None,
     {'glutes': .75, 'hamstrings': .65, 'quadriceps': .5, 'lower back': .8, 'traps': .5, 'lats': .35, 'middle back': .35,
      'forearms': .35, 'grip': .5, 'calves': .15, 'abdominals': .25},
     ['Escamilla 2002', 'Hamlyn 2007']),
    (r'hip thrust|bridge', None, {'hamstrings': .35, 'abdominals': .15, 'lower back': .2, 'quadriceps': .35}, ['Contreras 2015']),
    (r'hyperextension|back extension|superman', None, {'glutes': .55, 'hamstrings': .55}, ['Bourne 2017', 'Ono 2011']),
    (r'leg curl|hamstring curl|glute.?ham', None, {'calves': .3, 'glutes': .3}, ['Bourne 2017', 'McAllister 2014']),
    (r'kettlebell.*swing|swings?$', None, {'hamstrings': .65, 'lower back': .45, 'shoulders': .2, 'grip': .35, 'abdominals': .2},
     ['McGill 2009', 'Escamilla 2002']),
    (r'\bclean\b|snatch', None, {'shoulders': .4, 'traps': .55, 'glutes': .55, 'hamstrings': .5, 'quadriceps': .55, 'grip': .35, 'lower back': .4},
     ['Escamilla 2002', 'Ekstrom 2003']),
    (r'abduction|clamshell|monster walk|band walk|fire hydrant', None, {'glutes': .45}, ['Distefano 2009', 'Selkowitz 2013']),
    (r'adduction', None, {}, ['Serner 2014']),
    # Core
    (r'sit-?up|pull-in|leg tuck|knee tuck|tuck crunch|reverse crunch|butt-ups|hip raise|jackknife|v-up|cocoons|knee/hip raise|air bike|leg raise|knee raise',
     'abdominals', {'quadriceps': .3}, ['Andersson 1997', 'Escamilla 2006']),
    (r'twist|side bend|oblique|judo flip|heel touch|wood chop|cable lift', 'abdominals', {'lower back': .3}, ['Escamilla 2006', 'McGill 2009']),
    (r'plank|rollout|ab wheel|bear crawl|fallout', None, {'shoulders': .3, 'serratus': .35, 'glutes': .2, 'lats': .3, 'lower back': .2},
     ['Escamilla 2006', 'Ludewig 2004']),
    # Carries / grip
    (r'farmer|carry|walk with', None,
     {'forearms': .6, 'traps': .55, 'abdominals': .4, 'glutes': .2, 'quadriceps': .2, 'hamstrings': .15, 'lower back': .3},
     ['McGill 2009', 'Mogk 2003']),
    (r'hang\b|hanging', None, {'grip': .6, 'forearms': .45, 'lats': .25}, ['Mogk 2003', 'Youdas 2010']),
]
# Default by muscle when no rule says otherwise: stabilisers that EMG studies typically record at
# low levels (roughly 10-30% MVC) while the main muscle works hard.
DEFAULT = {'grip': .2, 'forearms': .2, 'calves': .15, 'abdominals': .2, 'lower back': .25, 'neck': .2, 'traps': .3,
           'serratus': .2, 'rotator cuff': .25, 'hip flexors': .3, 'shins': .2}
DEFAULT_REFS = ['Hamlyn 2007', 'Reinold 2009']
FALLBACK = .3

# Exact weights for particular exercises (checked before the rules).
OVERRIDES = {
    # Forearm-plank scapular protraction: serratus leads, abdominals hold the plank (Ludewig 2004, Decker 1999).
    "Push-Up Plus": {"abdominals": .45, "shoulders": .35, "chest": .2},
}
OVERRIDE_REFS = {"Push-Up Plus": ['Ludewig 2004', 'Decker 1999']}
