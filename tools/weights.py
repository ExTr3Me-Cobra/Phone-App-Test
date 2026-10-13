# How hard each secondary muscle works compared with the primary (primary = 1.0).
# Rules are checked in order; the first rule that matches the exercise name (and primary
# muscle, if given) and lists the muscle sets its weight. Otherwise DEFAULT applies.
RULES = [
    # Pressing
    (r'close-?grip.*(bench|press)|jm press|tate press|board press|pin press', 'triceps', {'chest': .6, 'shoulders': .4}),
    (r'incline.*(press|bench)', None, {'shoulders': .6, 'triceps': .45, 'serratus': .25}),
    (r'decline.*(press|bench)', None, {'triceps': .5, 'shoulders': .3}),
    (r'bench press|chest press|floor press|smith.*press|machine bench', None, {'triceps': .5, 'shoulders': .45, 'serratus': .15}),
    (r'push-?ups?\b|push up', None, {'triceps': .45, 'shoulders': .4, 'serratus': .3, 'abdominals': .2, 'chest': .6}),
    (r'dips?\b', None, {'chest': .6, 'triceps': .6, 'shoulders': .4}),
    (r'fl(y|ye|yes)\b|crossover|pec deck|butterfly|iron cross', 'chest', {'shoulders': .3, 'biceps': .1}),
    (r'pullover', None, {'lats': .6, 'chest': .6, 'serratus': .4, 'triceps': .3}),
    (r'shoulder press|military press|overhead press|push press|arnold|jerk|landmine press|bradford', None,
     {'triceps': .5, 'traps': .3, 'serratus': .3, 'chest': .2, 'abdominals': .15}),
    (r'lateral raise|side laterals|deltoid raise|power partials', None, {'traps': .35}),
    (r'front .*raise|raise over head|raise above head', None, {'chest': .25, 'traps': .25, 'serratus': .25}),
    (r'upright row', None, {'traps': .6, 'biceps': .3, 'forearms': .2}),
    (r'rear delt|reverse fl|face pull|band pull apart|y-t-w', None, {'middle back': .55, 'traps': .4, 'rotator cuff': .45, 'shoulders': .6}),
    (r'external rotation|internal rotation|cuban', None, {'shoulders': .3}),
    # Pulling
    (r'pull-?ups?|chin-?ups?|muscle up|towel pull', None, {'biceps': .5, 'middle back': .5, 'forearms': .3, 'grip': .35, 'shoulders': .2, 'abdominals': .15}),
    (r'pulldown', None, {'biceps': .45, 'middle back': .45, 'forearms': .25, 'grip': .2, 'shoulders': .2}),
    (r'(?<!upright )row\b|rows\b', None, {'middle back': .6, 'lats': .6, 'biceps': .4, 'shoulders': .35, 'traps': .35, 'forearms': .25, 'grip': .25, 'lower back': .2}),
    (r'shrug', None, {'forearms': .2, 'grip': .3, 'middle back': .2}),
    # Arms
    (r'hammer|reverse curl|zottman|reverse.*preacher|reverse plate', None, {'forearms': .55, 'grip': .3}),
    (r'curl', 'biceps', {'forearms': .3, 'grip': .15}),
    (r'kickback|skull|triceps extension|tricep extension|pushdown|overhead triceps|triceps press', 'triceps', {'forearms': .1, 'shoulders': .15, 'chest': .2}),
    (r'wrist', None, {'grip': .4}),
    # Legs
    (r'front.*squat|goblet|zercher', None, {'glutes': .45, 'adductors': .35, 'abdominals': .3, 'lower back': .25, 'hamstrings': .2, 'calves': .1}),
    (r'squat', None, {'glutes': .6, 'adductors': .45, 'hamstrings': .3, 'lower back': .3, 'calves': .15, 'abdominals': .2, 'quadriceps': .7}),
    (r'lunge|split squat|step-?up|step down', None, {'glutes': .6, 'hamstrings': .3, 'adductors': .35, 'calves': .2, 'quadriceps': .7}),
    (r'leg press|hack squat', None, {'glutes': .5, 'adductors': .35, 'hamstrings': .25, 'calves': .1}),
    (r'stiff|romanian|good morning', None, {'glutes': .7, 'lower back': .45, 'hamstrings': .8, 'forearms': .2, 'grip': .3, 'traps': .2}),
    (r'deadlift|rack pull', None, {'glutes': .8, 'hamstrings': .75, 'quadriceps': .45, 'lower back': .7, 'traps': .45,
                                   'lats': .35, 'middle back': .35, 'forearms': .35, 'grip': .45, 'calves': .15, 'abdominals': .2}),
    (r'hip thrust|bridge', None, {'hamstrings': .35, 'abdominals': .15, 'lower back': .15, 'quadriceps': .2}),
    (r'hyperextension|back extension|superman', None, {'glutes': .5, 'hamstrings': .45}),
    (r'leg curl|hamstring curl|glute.?ham', None, {'calves': .2, 'glutes': .3}),
    (r'kettlebell.*swing|swings?$', None, {'hamstrings': .6, 'lower back': .4, 'shoulders': .2, 'grip': .35, 'abdominals': .2}),
    (r'\bclean\b|snatch', None, {'shoulders': .4, 'traps': .5, 'glutes': .5, 'hamstrings': .5, 'quadriceps': .5, 'grip': .3, 'lower back': .35}),
    (r'abduction|clamshell|monster walk|band walk|fire hydrant', None, {'glutes': .5}),
    (r'adduction', None, {}),
    # Core
    (r'sit-?up|pull-in|leg tuck|knee tuck|tuck crunch|reverse crunch|butt-ups|hip raise|jackknife|v-up|cocoons|knee/hip raise|air bike|leg raise|knee raise', 'abdominals', {'quadriceps': .35}),
    (r'twist|side bend|oblique|judo flip|heel touch|wood chop|cable lift', 'abdominals', {'lower back': .25}),
    (r'plank|rollout|ab wheel|bear crawl|fallout', None, {'shoulders': .3, 'serratus': .35, 'glutes': .2, 'lats': .25, 'lower back': .2}),
    # Carries / grip
    (r'farmer|carry|walk with', None, {'forearms': .6, 'traps': .5, 'abdominals': .35, 'glutes': .2, 'quadriceps': .2, 'hamstrings': .15, 'lower back': .25}),
    (r'hang\b|hanging', None, {'grip': .6, 'forearms': .45, 'lats': .2}),
]
# Default by muscle when no rule says otherwise (stabilisers count little).
DEFAULT = {'grip': .2, 'forearms': .2, 'calves': .15, 'abdominals': .2, 'lower back': .25, 'neck': .2, 'traps': .3,
           'serratus': .2, 'rotator cuff': .25}
FALLBACK = .3
