	var mat = StandardMaterial3D.new()
	mat.albedo_color = Color(0.12,0.75,1.0,0.7)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.emission_enabled = true
	mat.emission = Color(0.05,0.5,1.0)
	mat.emission_energy_multiplier = 4.0
	mesh.material = mat
	mi.mesh = mesh
	mi.rotation_degrees.x = 90
	mi.global_position = player.global_position + Vector3(0,0.05,0)
	add_child(mi)
	var tw = create_tween()
	tw.tween_property(mi,"scale",Vector3(3.0,3.0,3.0),0.32).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
	tw.tween_callback(mi.queue_free)

func spawn_hit_flash(pos: Vector3, color: Color):
	var mi = MeshInstance3D.new()
	var mesh = SphereMesh.new()
	mesh.radius = 0.3
	mesh.height = 0.55
	var mat = StandardMaterial3D.new()
	mat.albedo_color = Color(color.r,color.g,color.b,0.7)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.emission_enabled = true
	mat.emission = color
	mat.emission_energy_multiplier = 6.0
	mesh.material = mat
	mi.mesh = mesh
	mi.global_position = pos
	add_child(mi)
	var tw = create_tween()
	tw.tween_property(mi,"scale",Vector3(2.8,2.8,2.8),0.2)
	tw.tween_callback(mi.queue_free)

func spawn_absorb_burst(pos: Vector3, color: Color):
	for i in 7:
		var orb = make_energy_orb(color,0.07)
		orb.global_position = pos + Vector3(sin(i*2.0)*0.35,0.2+(i%3)*0.2,cos(i*2.0)*0.35)
		add_child(orb)
		var tw = create_tween()
		tw.tween_property(orb,"global_position",player.global_position+Vector3(0,0.55,0),0.42+i*0.025).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
		tw.tween_callback(orb.queue_free)

func spawn_absorb_stream(from: Vector3, to: Vector3):
	for i in 13:
		var orb = make_energy_orb(Color(0.56,0.18,1.0),0.055+0.01*(i%3))
		orb.global_position = from + Vector3(sin(i*1.7)*0.25,0.2+0.07*i,cos(i*1.3)*0.25)
		add_child(orb)
		var tw = create_tween()
		tw.tween_interval(i*0.025)
		tw.tween_property(orb,"global_position",to,0.5).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
		tw.tween_callback(orb.queue_free)

func make_energy_orb(color: Color, radius: float) -> MeshInstance3D:
	var mi = MeshInstance3D.new()
	var s = SphereMesh.new()
	s.radius = radius
	s.height = radius*2.0
	var mat = StandardMaterial3D.new()
	mat.albedo_color = color
	mat.emission_enabled = true
	mat.emission = color
	mat.emission_energy_multiplier = 5.5
	s.material = mat
	mi.mesh = s
	return mi

func _input(event):
	var size = get_viewport().get_visible_rect().size
	if event is InputEventScreenTouch:
		if event.pressed:
			if event.position.x < size.x*0.45 and event.position.y > size.y*0.35 and left_finger == -1:
				left_finger = event.index
				joy_origin = event.position
				joy_now = event.position
				joy_vector = Vector2.ZERO
				hud.set_joystick(joy_origin, joy_now, true)
			elif event.position.x > size.x*0.45 and event.position.y < size.y-250 and look_finger == -1:
				look_finger = event.index
		else:
			if event.index == left_finger:
				left_finger = -1
				joy_vector = Vector2.ZERO
				hud.set_joystick(Vector2.ZERO, Vector2.ZERO, false)
			elif event.index == look_finger:
				look_finger = -1
	elif event is InputEventScreenDrag:
		if event.index == left_finger:
			var d = event.position - joy_origin
			if d.length() > JOY_RADIUS: d = d.normalized()*JOY_RADIUS
			joy_now = joy_origin + d
			joy_vector = Vector2(d.x/JOY_RADIUS, d.y/JOY_RADIUS)
			hud.set_joystick(joy_origin, joy_now, true)
		elif event.index == look_finger:
			yaw -= event.relative.x * 0.008
			pitch = clamp(pitch - event.relative.y * 0.006, -0.74, 0.18)
	elif event is InputEventMouseMotion and Input.is_mouse_button_pressed(MOUSE_BUTTON_RIGHT):
		yaw -= event.relative.x * 0.006
		pitch = clamp(pitch - event.relative.y * 0.006, -0.74, 0.18)
