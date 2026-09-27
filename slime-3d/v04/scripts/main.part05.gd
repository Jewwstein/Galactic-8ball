	# Cave bat now behaves like an encounter rather than a static prop.
	if bat and not bat_dead:
		var to_player = player.global_position - bat.global_position
		var dist = to_player.length()
		if dist < 8.5:
			var target = player.global_position + Vector3(0,1.5,0)
			var desired = bat.global_position.direction_to(target)
			bat.global_position += desired * delta * (1.8 if dist > 3.2 else 0.7)
		else:
			bat.position = bat_base + Vector3(sin(cave_time*1.5)*1.2, sin(cave_time*3.0)*0.34, cos(cave_time*1.2)*0.5)
		bat.look_at(Vector3(player.global_position.x, bat.global_position.y, player.global_position.z), Vector3.UP)
		bat_attack_timer -= delta
		if dist < 2.35 and bat_attack_timer <= 0.0:
			bat_attack_timer = 1.7
			if dodge_timer <= 0.0:
				player_hp = max(0, player_hp-12)
				hud.player_hp = player_hp
				hud.flash("Cave Bat hit — 12 damage")
				spawn_hit_flash(player.global_position + Vector3(0,0.65,0), Color(1.0,0.12,0.16))
				if player_hp <= 0:
					respawn_player()

	# collect crystals by proximity
	for c in crystals.duplicate():
		if is_instance_valid(c) and player.global_position.distance_to(c.global_position) < 1.25:
			essence += 10
			hud.essence = essence
			hud.flash("ANALYSIS: Mana Crystal absorbed. Essence +10")
			spawn_absorb_burst(c.global_position, Color(0.15,0.72,1.0))
			crystals.erase(c)
			c.queue_free()

func respawn_player():
	player_hp = 100
	hud.player_hp = player_hp
	player.global_position = Vector3(0,0.65,3.0)
	hud.flash("SOUL CORE stabilized. Body reconstructed.", 3000)

func do_attack():
	if attack_cooldown > 0.0: return
	attack_cooldown = 0.42
	spawn_attack_arc()
	if bat_dead:
		hud.flash("Target defeated. ABSORB can analyze remains.")
		return
	var dist = player.global_position.distance_to(bat.global_position)
	if dist > 3.35:
		hud.flash("No target in striking range.")
		return
	bat_hp -= 1
	hud.enemy_hp = bat_hp
	player_visual.scale = Vector3(1.42,0.68,1.42)
	spawn_hit_flash(bat.global_position, Color(0.16,0.82,1.0))
	if bat_hp <= 0:
		bat_dead = true
		hud.enemy_dead = true
		bat.scale = Vector3(1.0,0.35,1.0)
		bat.rotation_degrees.x = 80
		hud.flash("ANALYSIS: Cave Bat defeated. Trait signature: ECHO SENSE.", 3000)
	else:
		hud.flash("Gel Impact — Cave Bat HP %d/5" % bat_hp)

func do_dodge():
	if dodge_cooldown > 0.0: return
	dodge_cooldown = 0.8
	dodge_timer = 0.27
	spawn_dodge_ring()
	hud.flash("Fluid Dash")

func do_absorb():
	if not bat_dead:
		hud.flash("ABSORB requires a defeated target.")
		return
	if not is_instance_valid(bat): return
	if player.global_position.distance_to(bat.global_position) > 3.4:
		hud.flash("Move closer to the defeated creature.")
		return
	spawn_absorb_stream(bat.global_position, player.global_position + Vector3(0,0.55,0))
	bat.queue_free()
	bat = null
	essence += 35
	morph += 20
	hud.essence = essence
	hud.morph = morph
	hud.flash("ANALYSIS COMPLETE — ECHO SENSE acquired. Morph Data +20%", 3600)

func spawn_attack_arc():
	var pivot = Node3D.new()
	pivot.global_position = player.global_position + Vector3(0,0.55,0)
	pivot.rotation.y = player.rotation.y
	add_child(pivot)
	var mi = MeshInstance3D.new()
	var mesh = TorusMesh.new()
	mesh.inner_radius = 0.7
	mesh.outer_radius = 0.79
	mesh.rings = 10
	mesh.ring_segments = 24
	var mat = StandardMaterial3D.new()
	mat.albedo_color = Color(0.2,0.88,1.0,0.78)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.emission_enabled = true
	mat.emission = Color(0.1,0.72,1.0)
	mat.emission_energy_multiplier = 5.0
	mesh.material = mat
	mi.mesh = mesh
	mi.rotation_degrees.x = 90
	mi.position.z = -0.85
	pivot.add_child(mi)
	var tw = create_tween()
	tw.set_parallel(true)
	tw.tween_property(mi,"scale",Vector3(1.8,1.8,1.8),0.22)
	tw.tween_property(mi,"position:z",-2.0,0.22)
	tw.set_parallel(false)
	tw.tween_callback(pivot.queue_free)

func spawn_dodge_ring():
	var mi = MeshInstance3D.new()
	var mesh = TorusMesh.new()
	mesh.inner_radius = 0.45
	mesh.outer_radius = 0.52
