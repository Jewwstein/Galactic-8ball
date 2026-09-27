		sphere.radius = 0.58
		var sm = make_slime_material()
		sphere.material = sm
		player_visual.mesh = sphere
		player.add_child(player_visual)

	# Inner soul core and soft underglow make the body feel translucent even on mobile.
	var core = MeshInstance3D.new()
	var core_mesh = SphereMesh.new()
	core_mesh.radius = 0.19
	core_mesh.height = 0.36
	var core_mat = StandardMaterial3D.new()
	core_mat.albedo_color = Color(0.32,0.92,1.0)
	core_mat.emission_enabled = true
	core_mat.emission = Color(0.05,0.78,1.0)
	core_mat.emission_energy_multiplier = 4.8
	core_mesh.material = core_mat
	core.mesh = core_mesh
	core.position.y = 0.05
	player.add_child(core)

	var underglow = OmniLight3D.new()
	underglow.position = Vector3(0,0.18,0)
	underglow.light_color = Color(0.12,0.78,1.0)
	underglow.light_energy = 1.35
	underglow.omni_range = 2.7
	player.add_child(underglow)

	camera_pivot = Node3D.new()
	camera_pivot.position = Vector3(0,1.35,0)
	player.add_child(camera_pivot)
	spring = SpringArm3D.new()
	spring.spring_length = 4.7
	spring.margin = 0.16
	camera_pivot.add_child(spring)
	camera = Camera3D.new()
	camera.current = true
	camera.fov = 67
	spring.add_child(camera)

func make_slime_material() -> StandardMaterial3D:
	var sm = StandardMaterial3D.new()
	sm.albedo_color = Color(0.08,0.58,0.95,0.68)
	sm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	sm.roughness = 0.17
	sm.metallic = 0.08
	sm.emission_enabled = true
	sm.emission = Color(0.015,0.14,0.25)
	sm.emission_energy_multiplier = 1.8
	sm.rim_enabled = true
	sm.rim = 0.72
	sm.rim_tint = 0.42
	return sm

func apply_slime_material(node: Node):
	if node is MeshInstance3D:
		var mesh_node := node as MeshInstance3D
		if mesh_node.mesh:
			for i in mesh_node.mesh.get_surface_count():
				mesh_node.set_surface_override_material(i, make_slime_material())
	for child in node.get_children():
		apply_slime_material(child)

func build_enemy():
	var found = load("res://assets/characters/Bat.glb")
	bat = Node3D.new()
	bat.position = Vector3(0,2.2,-13.0)
	bat_base = bat.position
	add_child(bat)
	if found is PackedScene:
		bat_visual = found.instantiate()
		bat_visual.scale = Vector3(1.35,1.35,1.35)
		bat.add_child(bat_visual)
		auto_play_animation(bat_visual)
	else:
		bat_visual = MeshInstance3D.new()
		var s = SphereMesh.new()
		s.radius = 0.45
		bat_visual.mesh = s
		bat.add_child(bat_visual)
	var red = OmniLight3D.new()
	red.light_color = Color(0.8,0.08,0.14)
	red.light_energy = 0.45
	red.omni_range = 2.0
	bat.add_child(red)

func auto_play_animation(node: Node):
	if node is AnimationPlayer:
		var names = node.get_animation_list()
		for name in names:
			if str(name).to_lower() != "reset":
				node.play(name)
				return
	for child in node.get_children():
		auto_play_animation(child)

func build_crystals():
	var positions = [
		Vector3(-2.6,0.75,-3.2),Vector3(2.2,0.75,-6.8),Vector3(-2.0,0.75,-10.5),
		Vector3(2.5,0.75,-18.0),Vector3(-2.8,0.75,-24.0),Vector3(4.4,0.75,-25.5),Vector3(-4.3,0.75,-16.0)
	]
	for i in positions.size():
		var area = Area3D.new()
		area.position = positions[i]
		area.set_meta("phase", float(i)*0.7)
		var cluster = Node3D.new()
		cluster.name = "CrystalVisual"
		area.add_child(cluster)
		for j in 4:
			var mi = MeshInstance3D.new()
			var cm = CylinderMesh.new()
			cm.top_radius = 0.0
			cm.bottom_radius = 0.17 + 0.045*j
			cm.height = 0.85 + 0.32*j
			var mat = StandardMaterial3D.new()
			var col = Color(0.08+0.06*j,0.68,1.0) if i % 2 == 0 else Color(0.55,0.16+0.08*j,1.0)
			mat.albedo_color = col
			mat.emission_enabled = true
			mat.emission = col
			mat.emission_energy_multiplier = 4.4
