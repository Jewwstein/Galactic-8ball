		rock_mesh.radius = rng.randf_range(0.22,0.6)
		rock_mesh.height = rock_mesh.radius * rng.randf_range(1.0,1.55)
		var rmat = StandardMaterial3D.new()
		rmat.albedo_color = Color(0.075,0.09,0.12).lightened(rng.randf_range(0.0,0.08))
		rmat.roughness = 0.98
		rock_mesh.material = rmat
		rock.mesh = rock_mesh
		rock.scale = Vector3(rng.randf_range(0.7,1.45), rng.randf_range(0.45,0.9), rng.randf_range(0.7,1.5))
		rock.position = Vector3(rng.randf_range(-6.6,6.6), 0.12, rng.randf_range(-27.0,5.0))
		add_child(rock)

	for p in [Vector3(-5.4,0,-5),Vector3(5.7,0,-11),Vector3(-5.2,0,-17),Vector3(5.0,0,-25)]:
		spawn_stalagmite_cluster(p)

	for p in [Vector3(-4.6,0.18,-1.5),Vector3(4.8,0.18,-7.5),Vector3(-4.2,0.18,-14.2),Vector3(4.7,0.18,-21.8)]:
		spawn_mushroom_patch(p)

	spawn_mana_pool(Vector3(-3.4,0.015,-11.8), Vector2(3.2,2.0))
	spawn_mana_pool(Vector3(3.8,0.015,-20.8), Vector2(2.5,1.6))

func spawn_cave_piece(path: String, pos: Vector3, rot_y_deg: float):
	var packed = load(path)
	if packed is PackedScene:
		var inst = packed.instantiate()
		inst.position = pos
		inst.rotation_degrees.y = rot_y_deg
		add_child(inst)

func spawn_stalagmite_cluster(pos: Vector3):
	for i in 4:
		var m = MeshInstance3D.new()
		var cone = CylinderMesh.new()
		cone.top_radius = 0.02
		cone.bottom_radius = 0.34 + i*0.05
		cone.height = 1.4 + i*0.42
		var mat = StandardMaterial3D.new()
		mat.albedo_color = Color(0.055,0.07,0.095)
		mat.roughness = 0.95
		cone.material = mat
		m.mesh = cone
		m.position = pos + Vector3((i-1.5)*0.34, cone.height*0.5, sin(i*1.7)*0.28)
		m.rotation_degrees.z = (i-1.5)*4.0
		add_child(m)

func spawn_mushroom_patch(pos: Vector3):
	for i in 5:
		var root = Node3D.new()
		root.position = pos + Vector3((i-2)*0.32,0,cos(i*2.1)*0.26)
		var stem = MeshInstance3D.new()
		var stem_mesh = CylinderMesh.new()
		stem_mesh.top_radius = 0.055
		stem_mesh.bottom_radius = 0.07
		stem_mesh.height = 0.32 + (i%2)*0.1
		var stem_mat = StandardMaterial3D.new()
		stem_mat.albedo_color = Color(0.24,0.28,0.33)
		stem_mesh.material = stem_mat
		stem.mesh = stem_mesh
		stem.position.y = stem_mesh.height*0.5
		root.add_child(stem)
		var cap = MeshInstance3D.new()
		var cap_mesh = SphereMesh.new()
		cap_mesh.radius = 0.14 + 0.025*(i%3)
		cap_mesh.height = 0.16
		var cap_mat = StandardMaterial3D.new()
		cap_mat.albedo_color = Color(0.22,0.58,1.0)
		cap_mat.emission_enabled = true
		cap_mat.emission = Color(0.08,0.35,1.0)
		cap_mat.emission_energy_multiplier = 2.4
		cap_mesh.material = cap_mat
		cap.mesh = cap_mesh
		cap.position.y = stem_mesh.height + 0.04
		root.add_child(cap)
		add_child(root)

func spawn_mana_pool(pos: Vector3, size: Vector2):
	var mi = MeshInstance3D.new()
	var pm = PlaneMesh.new()
	pm.size = size
	var mat = StandardMaterial3D.new()
	mat.albedo_color = Color(0.035,0.22,0.38,0.74)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.emission_enabled = true
	mat.emission = Color(0.02,0.22,0.5)
	mat.emission_energy_multiplier = 1.9
	mat.roughness = 0.08
	pm.material = mat
	mi.mesh = pm
	mi.position = pos
	add_child(mi)
	var light = OmniLight3D.new()
	light.position = pos + Vector3(0,0.45,0)
	light.light_color = Color(0.1,0.42,0.9)
	light.light_energy = 0.65
	light.omni_range = 3.0
	add_child(light)
	glow_lights.append(light)

func build_player():
	player = CharacterBody3D.new()
	player.name = "PlayerSlime"
	player.position = Vector3(0,0.65,3.0)
	add_child(player)
	var collider = CollisionShape3D.new()
	var capsule = CapsuleShape3D.new()
	capsule.radius = 0.48
	capsule.height = 1.0
	collider.shape = capsule
	player.add_child(collider)

	var found = load("res://assets/characters/Slime.glb")
	if found is PackedScene:
		player_visual = found.instantiate()
		player_visual.scale = Vector3(1.18,1.0,1.18)
		player.add_child(player_visual)
		apply_slime_material(player_visual)
		auto_play_animation(player_visual)
	else:
		player_visual = MeshInstance3D.new()
		var sphere = SphereMesh.new()
		sphere.height = 1.05
