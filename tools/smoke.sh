#!/bin/bash
# Boots a real Fabric 26.3 server with the mod, spawns a happy ghast, applies upgrades through the same code
# path as the right-click (the ghast is spawned with NoAI so it cannot wander out of range), checks attributes + tags + level cap, then restarts the server and checks they persist.
set -u
MC=26.3; LOADER=0.19.5
ROOT=$(pwd); RUN=$ROOT/smoke-run; rm -rf "$RUN"; mkdir -p "$RUN/mods"; cd "$RUN"
JAR=$(ls "$ROOT"/build/libs/happy-ghast-upgrades-*.jar | grep -v sources | head -1)
cp "$JAR" mods/
INST=$(curl -sS https://meta.fabricmc.net/v2/versions/installer | python3 -c "import sys,json;print([i for i in json.load(sys.stdin) if i['stable']][0]['version'])")
curl -sSfL -o server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INST/server/jar"
FAPI=$(curl -sS -G "https://api.modrinth.com/v2/project/fabric-api/version" --data-urlencode "game_versions=[\"$MC\"]" --data-urlencode 'loaders=["fabric"]' | python3 -c "import sys,json;v=json.load(sys.stdin)[0];print(v['files'][0]['url'])")
curl -sSfL -o mods/fabric-api.jar "$FAPI"
echo eula=true > eula.txt
cat > server.properties <<P
online-mode=false
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
difficulty=easy
max-tick-time=-1
pause-when-empty-seconds=-1
P

FAIL=0
send() { echo "$1" > in; }
start_server() {
  rm -f in; mkfifo in
  : > server.log
  tail -f in | java -Xmx2G -jar server.jar nogui >> server.log 2>&1 &
  SRV=$!
  for i in $(seq 1 120); do grep -q 'Done (' server.log && break; sleep 2; done
  if ! grep -q 'Done (' server.log; then echo "SERVER DID NOT START"; tail -60 server.log; cp server.log "$ROOT/smoke.log"; exit 1; fi
}
stop_server() {
  send "save-all flush"; sleep 4; send "stop"
  for i in $(seq 1 60); do kill -0 $SRV 2>/dev/null || break; sleep 1; done
  pkill -f "tail -f in" 2>/dev/null; sleep 1
}
check() {   # check <log marker> <description>
  if grep -q "$1" server.log; then echo "PASS: $2"; else echo "FAIL: $2 (no $1 in log)"; FAIL=1; fi
}
nocheck() { # nocheck <log marker> <description>
  if grep -q "$1" server.log; then echo "FAIL: $2"; FAIL=1; else echo "PASS: $2"; fi
}

start_server
check 'Happy Ghast Upgrades loaded' "mod loaded"
check 'Happy Ghast Upgrades ready' "settings loaded at server start"
[ -f config/happyghastupgrades/settings.json ] && echo "PASS: settings.json written" || { echo "FAIL: settings.json missing"; FAIL=1; }
nocheck 'Parsing error loading recipe' "recipe parses without errors"
nocheck 'Failed to load recipe' "no recipe load failures"

send "forceload add -16 -16 16 16"; sleep 4
send "summon minecraft:happy_ghast 0 -58 0 {NoAI:1b}"; sleep 3
send "execute if entity @e[type=minecraft:happy_ghast] run say HGU_OK_ghast_spawned"; sleep 1
send "scoreboard objectives add hgu dummy"

# --- speed
send "execute store result score spd0 hgu run attribute @e[type=minecraft:happy_ghast,limit=1] minecraft:flying_speed get 1000"; sleep 1
send "execute positioned 0 -58 0 run happyghast apply speed"; sleep 2
send "execute store result score spd1 hgu run attribute @e[type=minecraft:happy_ghast,limit=1] minecraft:flying_speed get 1000"; sleep 1
send "execute if score spd1 hgu > spd0 hgu run say HGU_OK_speed_attribute_rose"
send "execute if entity @e[type=minecraft:happy_ghast,tag=hgu_speed_1] run say HGU_OK_speed_tag_1"
# --- health
send "execute positioned 0 -58 0 run happyghast apply health"; sleep 2
send "execute store result score hp hgu run attribute @e[type=minecraft:happy_ghast,limit=1] minecraft:max_health get"; sleep 1
send "execute if score hp hgu matches 24 run say HGU_OK_health_24"
send "execute if entity @e[type=minecraft:happy_ghast,tag=hgu_health_1] run say HGU_OK_health_tag_1"
# --- level cap: speed max is 4, so the 5th apply must be refused
for i in 2 3 4 5; do send "execute positioned 0 -58 0 run happyghast apply speed"; sleep 1; done
sleep 1
send "execute if entity @e[type=minecraft:happy_ghast,tag=hgu_speed_4] run say HGU_OK_speed_level_4"
send "execute unless entity @e[type=minecraft:happy_ghast,tag=hgu_speed_5] run say HGU_OK_speed_capped"
send "execute if entity @e[type=minecraft:happy_ghast,tag=hgu_speed_1] run say HGU_BAD_old_tag_left"
send "execute store result score spd4 hgu run attribute @e[type=minecraft:happy_ghast,limit=1] minecraft:flying_speed get 1000"
sleep 2

check 'HGU_OK_ghast_spawned' "happy ghast spawned"
check 'HGU_OK_speed_attribute_rose' "speed upgrade raises flying_speed"
check 'HGU_OK_speed_tag_1' "speed level stored as tag"
check 'HGU_OK_health_24' "health upgrade raises max_health 20 -> 24"
check 'HGU_OK_health_tag_1' "health level stored as tag"
check 'HGU_OK_speed_level_4' "speed reaches level 4"
check 'HGU_OK_speed_capped' "speed refused beyond max level"
nocheck 'HGU_BAD_old_tag_left' "old level tags are replaced"
nocheck 'Command failed' "no unexpected command failures"

# --- persistence across a restart
cp server.log "$ROOT/smoke.log"
stop_server
start_server
send "forceload add -16 -16 16 16"; sleep 5
send "execute store result score hp hgu run attribute @e[type=minecraft:happy_ghast,limit=1] minecraft:max_health get"; sleep 1
send "execute if score hp hgu matches 24 run say HGU_OK_health_persisted"
send "execute if entity @e[type=minecraft:happy_ghast,tag=hgu_speed_4] run say HGU_OK_speed_tag_persisted"
send "execute store result score spdr hgu run attribute @e[type=minecraft:happy_ghast,limit=1] minecraft:flying_speed get 1000"
sleep 2
check 'HGU_OK_health_persisted' "health upgrade survives a restart"
check 'HGU_OK_speed_tag_persisted' "speed level survives a restart"

{ echo "=== after restart ==="; cat server.log; } >> "$ROOT/smoke.log"
stop_server
if [ $FAIL -ne 0 ]; then echo "SMOKE TEST FAILED"; grep -n -i -E 'error|exception|warn.*happy' server.log | head -40; exit 1; fi
echo "SMOKE TEST PASSED"
