			mat.roughness = 0.12
			cm.material = mat
			mi.mesh = cm
			mi.position = Vector3((j-1.5)*0.24, 0.12*j, 0.07*j)
			mi.rotation_degrees.z = (j-1.5)*11
			mi.rotation_degrees.y = j*37
			cluster.add_child(mi)
		var light = OmniLight3D.new()
		light.omni_range = 4.8
		light.light_energy = 1.6
		light.set_meta("base_energy", 1.6)
		light.light_color = Color(0.15,0.68,1.0) if i%2==0 else Color(0.62,0.24,1.0)
		area.add_child(light)
		glow_lights.append(light)
		var cs = CollisionShape3D.new()
		var ss = SphereShape3D.new()
		ss.radius = 0.82
		cs.shape = ss
		area.add_child(cs)
		add_child(area)
		crystals.append(area)

func build_ui():
	var canvas = CanvasLayer.new()
	add_child(canvas)
	var hud_script = load("res://scripts/hud.gd")
	hud = Control.new()
	hud.set_script(hud_script)
	hud.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	canvas.add_child(hud)
	hud.player_hp = player_hp
	hud.enemy_hp = bat_hp

	attack_button = make_button("ATTACK", Color(0.68,0.09,0.13,0.86), Vector2(-448,-166))
	dodge_button = make_button("DODGE", Color(0.08,0.36,0.72,0.86), Vector2(-286,-282))
	absorb_button = make_button("ABSORB", Color(0.42,0.1,0.68,0.86), Vector2(-172,-158))
	attack_button.pressed.connect(do_attack)
	dodge_button.pressed.connect(do_dodge)
	absorb_button.pressed.connect(do_absorb)

func make_button(label: String, color: Color, offset: Vector2) -> Button:
	var b = Button.new()
	b.text = label
	b.custom_minimum_size = Vector2(164,100)
	b.anchor_left = 1.0
	b.anchor_top = 1.0
	b.anchor_right = 1.0
	b.anchor_bottom = 1.0
	b.offset_left = offset.x
	b.offset_top = offset.y
	b.offset_right = offset.x + 164
	b.offset_bottom = offset.y + 100
	b.add_theme_font_size_override("font_size", 23)
	var style = StyleBoxFlat.new()
	style.bg_color = color
	style.corner_radius_top_left = 42
	style.corner_radius_top_right = 42
	style.corner_radius_bottom_left = 42
	style.corner_radius_bottom_right = 42
	style.border_width_left = 3; style.border_width_top = 3; style.border_width_right = 3; style.border_width_bottom = 3
	style.border_color = Color(0.68,0.92,1,0.78)
	style.shadow_color = Color(0.0,0.0,0.0,0.5)
	style.shadow_size = 10
	b.add_theme_stylebox_override("normal", style)
	var pressed = style.duplicate()
	pressed.bg_color = color.lightened(0.18)
	b.add_theme_stylebox_override("pressed", pressed)
	hud.add_child(b)
	return b

func _physics_process(delta):
	if not player: return
	cave_time += delta
	attack_cooldown = max(0.0, attack_cooldown-delta)
	dodge_cooldown = max(0.0, dodge_cooldown-delta)

	if not player.is_on_floor():
		player.velocity.y -= GRAVITY * delta
	else:
		player.velocity.y = -0.1

	var input_vec = joy_vector
	if input_vec.length() < 0.05:
		input_vec = Input.get_vector("move_left","move_right","move_forward","move_back")
	var basis = Basis(Vector3.UP, yaw)
	var dir = (basis * Vector3(input_vec.x,0,input_vec.y)).normalized()
	var spd = DODGE_SPEED if dodge_timer > 0 else SPEED
	if dodge_timer > 0: dodge_timer -= delta
	player.velocity.x = dir.x * spd
	player.velocity.z = dir.z * spd
	if dir.length() > 0.1:
		var target_y = atan2(dir.x, dir.z)
		player.rotation.y = lerp_angle(player.rotation.y, target_y, delta*9.0)
		var pulse = sin(Time.get_ticks_msec()*0.018) * 0.075
		player_visual.scale.y = 1.0 + pulse
		player_visual.scale.x = 1.18 - pulse*0.52
		player_visual.scale.z = 1.18 - pulse*0.52
	else:
		var idle_breath = sin(Time.get_ticks_msec()*0.0033)*0.018
		player_visual.scale = player_visual.scale.lerp(Vector3(1.18-idle_breath,1.0+idle_breath,1.18-idle_breath), delta*5.0)
	player.move_and_slide()

	camera_pivot.rotation.y = yaw - player.rotation.y
	spring.rotation.x = pitch

	# Ambient light pulse.
	for i in glow_lights.size():
		if is_instance_valid(glow_lights[i]):
			var base_energy = float(glow_lights[i].get_meta("base_energy", 1.0))
			glow_lights[i].light_energy = base_energy * (0.9 + 0.16*(1.0 + sin(cave_time*1.2 + i))*0.5)

	# Crystals hover/rotate so the mana field reads visually from a distance.
	for c in crystals:
		if is_instance_valid(c):
			var phase = float(c.get_meta("phase",0.0))
			var vis = c.get_node_or_null("CrystalVisual")
			if vis:
				vis.rotation.y += delta*0.24
				vis.position.y = sin(cave_time*1.8+phase)*0.06

