# Exercises hidden from the list (name makes no sense, needs a partner or specialist equipment,
# coaching drills, or duplicates). They stay in the data so old workouts still show them.
REMOVE = {
 # Name is just a muscle (partner-assisted stretches)
 "Adductor/Groin", "Lying Glute", "Lying Hamstring", "Overhead Lat", "Overhead Triceps", "Seated Biceps",
 "Seated Front Deltoid", "Seated Glute", "Seated Hamstring", "Lying Prone Quadriceps", "Lying Bent Leg Groin",
 # Unclear or trivial stretches / moves
 "Pyramid", "Hug A Ball", "Looking At Ceiling", "Upward Stretch", "Elbows Back", "Upper Back-Leg Grab",
 "One Half Locust", "Shoulder Raise", "Lower Back Curl", "Bottoms Up", "Wind Sprints", "Dumbbell Raise",
 "Downward Facing Balance", "Squat with Plate Movers", "Anti-Gravity Press", "Hip Lift with Band",
 # Sprint / coaching drills
 "Linear 3-Part Start Technique", "Linear Acceleration Wall Drill", "Chest Push (multiple response)",
 "Chest Push (single response)", "Chest Push from 3 point stance", "Chest Push with Run Release",
 "Return Push from Stance", "Drop Push", "Single-Cone Sprint Drill", "Kneeling Arm Drill", "Moving Claw Series",
 "Rack Delivery", "Heavy Bag Thrust", "Bench Sprint", "Side Hop-Sprint", "Lunge Sprint", "Quick Leap", "Rocket Jump",
 "Single Leg Push-off", "Single-Leg Hop Progression", "Front Cone Hops (or hurdle hops)", "Lateral Cone Hops",
 "Carioca Quick Step", "Incline Push-Up Depth Jump", "Depth Jump Leap", "Single-Leg Stride Jump", "Stride Jump Crossover",
 # Partner medicine-ball throws
 "Medicine Ball Chest Pass", "Medicine Ball Scoop Throw", "Backward Medicine Ball Throw", "Standing Two-Arm Overhead Throw",
 "Supine Chest Throw", "Supine One-Arm Overhead Throw", "Supine Two-Arm Overhead Throw", "Catch and Overhead Throw",
 # Strongman events
 "Car Deadlift", "Conan's Wheel", "Circus Bell", "Keg Load", "Atlas Stones", "Atlas Stone Trainer", "Rickshaw Carry",
 "Rickshaw Deadlift", "Yoke Walk", "Power Stairs", "Log Lift",
 # Chains / reverse bands (specialist powerlifting setups)
 "Bench Press with Chains", "Box Squat with Chains", "Deadlift with Chains", "Floor Press with Chains", "Squat with Chains",
 "Sumo Deadlift with Chains", "Chain Press", "Chain Handle Extension", "Reverse Band Bench Press", "Reverse Band Box Squat",
 "Reverse Band Deadlift", "Reverse Band Power Squat", "Reverse Band Sumo Deadlift",
 # Sled variations (Sled Push and Sled Drag - Harness are kept)
 "Backward Drag", "Forward Drag with Press", "Sled Overhead Backward Walk", "Sled Overhead Triceps Extension",
 "Sled Reverse Flye", "Sled Row", "Bear Crawl Sled Drags", "Prowler Sprint",
 # Duplicates
 "Butt Lift (Bridge)", "Monster Walk (Band)", "Ab Roller", "Dead Bug (Alternating)", "Squat with Bands",
}

RENAME = {
 "Adductor": "Foam Roll: Adductors",
 "Anterior Tibialis-SMR": "Foam Roll: Shins",
 "Brachialis-SMR": "Foam Roll: Upper Arm (Brachialis)",
 "Calves-SMR": "Foam Roll: Calves",
 "Foot-SMR": "Foot Massage Ball Roll",
 "Hamstring-SMR": "Foam Roll: Hamstrings",
 "Iliotibial Tract-SMR": "Foam Roll: IT Band",
 "Latissimus Dorsi-SMR": "Foam Roll: Lats",
 "Lower Back-SMR": "Foam Roll: Lower Back",
 "Neck-SMR": "Massage Ball: Neck",
 "Peroneals-SMR": "Foam Roll: Outer Shin (Peroneals)",
 "Piriformis-SMR": "Foam Roll: Piriformis",
 "Quadriceps-SMR": "Foam Roll: Quads",
 "Rhomboids-SMR": "Foam Roll: Upper Back",
 "Thigh Abductor": "Hip Abduction (Machine)",
 "Thigh Adductor": "Hip Adduction (Machine)",
 "Kneeling Hip Flexor": "Kneeling Hip Flexor Stretch",
 "Standing Hip Flexors": "Standing Hip Flexor Stretch",
 "The Straddle": "Seated Straddle Stretch",
 "Ankle On The Knee": "Lying Figure-4 Glute Stretch",
 "Knee Across The Body": "Lying Spinal Twist Stretch",
 "Leg Lift": "Standing Glute Kickback (Bodyweight)",
 "Body-Up": "Forearm Plank to Push-Up (Body-Up)",
 "Cocoons": "Cocoons (Lying Tuck Crunch)",
}

# How each exercise is logged, where the source data guessed wrong. Bodyweight = reps only (no weight box).
REPS_ONLY = {
    # Pull-ups, chin-ups, dips, rows and push-ups on bars / rings / straps / a ball
    'Band Assisted Pull-Up', 'Bodyweight Mid Row', 'Bodyweight Walking Lunge', 'Decline Push-Up', 'Dips - Chest Version',
    'Floor Glute-Ham Raise', 'Gironda Sternum Chins', 'Hyperextensions (Back Extensions)', 'Inverted Row',
    'Inverted Row with Straps', 'Kipping Muscle Up', 'Knee/Hip Raise On Parallel Bars', 'London Bridges', 'Mixed Grip Chin',
    'Muscle Up', 'One Arm Chin-Up', 'Parallel Bar Dip', 'Platform Hamstring Slides', 'Prone Manual Hamstring', 'Ring Dips',
    'Rocky Pull-Ups/Pulldowns', 'Rope Climb', 'Scapular Pull-Up', 'Side To Side Chins', 'Single-Leg High Box Squat',
    'Suspended Fallout', 'Suspended Push-Up', 'Suspended Reverse Crunch', 'Suspended Row', 'Suspended Split Squat',
    'Ball Leg Curl', 'Exercise Ball Crunch', 'Exercise Ball Pull-In', 'Physioball Hip Bridge', 'Push-Ups With Feet On An Exercise Ball',
}
# Done for time, not reps.
TIMED_ONLY = {'Balance Board', 'Battling Ropes'}
# Weighted versions of bodyweight moves: the weight box is the weight you add.
WEIGHTED = {'Weighted Pull Ups', 'Weighted Bench Dip'}
