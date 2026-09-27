extends Node3D

const SPEED := 4.9
const DODGE_SPEED := 10.8
const GRAVITY := 18.0
const JOY_RADIUS := 85.0

var player: CharacterBody3D
var player_visual: Node3D
var camera_pivot: Node3D
var spring: SpringArm3D
var camera: Camera3D
var hud: Control
var attack_button: Button
var dodge_button: Button
var absorb_button: Button
var left_finger := -1
var look_finger := -1
var joy_origin := Vector2.ZERO
var joy_now := Vector2.ZERO
var joy_vector := Vector2.ZERO
var yaw := 0.0
var pitch := -0.24
var dodge_timer := 0.0
var dodge_cooldown := 0.0
var attack_cooldown := 0.0
var bat: Node3D
var bat_visual: Node3D
var bat_hp := 5
var bat_dead := false
var bat_base := Vector3.ZERO
var bat_attack_timer := 1.5
var player_hp := 100
var crystals: Array[Area3D] = []
var essence := 0
var morph := 0
var cave_time := 0.0
var glow_lights: Array[OmniLight3D] = []

func _ready():
	build_world()
	build_player()
	build_enemy()
	build_crystals()
	build_ui()
	hud.flash("ANALYSIS: Unknown cavern. Mana density unusually high.", 3400)

func build_world():
	var env_node = WorldEnvironment.new()
	var env = Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.004, 0.008, 0.018)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.11, 0.17, 0.28)
	env.ambient_light_energy = 0.43
	env.fog_enabled = true
	env.fog_light_color = Color(0.035,0.11,0.18)
	env.fog_density = 0.024
	env.fog_height = 1.5
	env.fog_height_density = 0.16
	env.glow_enabled = true
	env.glow_intensity = 1.15
	env.glow_bloom = 0.18
	env_node.environment = env
	add_child(env_node)

	var moon = DirectionalLight3D.new()
	moon.light_color = Color(0.32,0.5,0.82)
	moon.light_energy = 0.58
	moon.rotation_degrees = Vector3(-58,-22,0)
	moon.shadow_enabled = true
	moon.directional_shadow_max_distance = 32.0
	add_child(moon)

	# dark floor beneath the modular cave. This guarantees reliable collision in the prototype.
	var floor = StaticBody3D.new()
	var mesh = MeshInstance3D.new()
	var plane = PlaneMesh.new()
	plane.size = Vector2(46,46)
	var mat = StandardMaterial3D.new()
	mat.albedo_color = Color(0.026,0.033,0.045)
	mat.roughness = 0.92
	plane.material = mat
	mesh.mesh = plane
	floor.add_child(mesh)
	var shape = CollisionShape3D.new()
	var box = BoxShape3D.new()
	box.size = Vector3(46,0.2,46)
	shape.shape = box
	shape.position.y = -0.12
	floor.add_child(shape)
	add_child(floor)

	# Kenney CC0 modular cave pieces.
	spawn_cave_piece("res://assets/cave/room-large.glb", Vector3(0,0,0), 0)
	spawn_cave_piece("res://assets/cave/corridor-wide.glb", Vector3(0,0,-8), 0)
	spawn_cave_piece("res://assets/cave/corridor-wide.glb", Vector3(0,0,-15), 0)
	spawn_cave_piece("res://assets/cave/room-small.glb", Vector3(0,0,-23), 0)
	spawn_cave_piece("res://assets/cave/corridor-corner.glb", Vector3(7,0,-23), 90)

	# Invisible collision rails until the cave kit gets a proper authored collision pass.
	for spec in [
		[Vector3(-8,2,-10), Vector3(1,4,34)], [Vector3(8,2,-10), Vector3(1,4,34)],
		[Vector3(0,2,7), Vector3(17,4,1)], [Vector3(0,2,-29), Vector3(17,4,1)]
	]:
		var wall = StaticBody3D.new()
		var cs = CollisionShape3D.new()
		var bs = BoxShape3D.new()
		bs.size = spec[1]
		cs.shape = bs
		wall.position = spec[0]
		wall.add_child(cs)
		add_child(wall)

	# Procedural dressing: rocks, stalagmites, mushrooms and blue cave pools.
	var rng = RandomNumberGenerator.new()
	rng.seed = 30841
	for i in 30:
		var rock = MeshInstance3D.new()
		var rock_mesh = SphereMesh.new()
