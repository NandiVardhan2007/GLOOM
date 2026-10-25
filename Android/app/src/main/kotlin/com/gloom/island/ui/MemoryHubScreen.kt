package com.gloom.island.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gloom.island.ai.AiMemory

/**
 * AI Memory Hub: Full view, search, categorize, add, edit, and delete screen
 * for all long-term memories, user relationships, app defaults, and learned recipes.
 */
@Composable
fun MemoryHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    var entities by remember { mutableStateOf<List<AiMemory.Entity>>(emptyList()) }
    var recipes by remember { mutableStateOf<List<AiMemory.LearnedRecipe>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") } // "all", "contact", "app_default", "personal", "general", "recipes"

    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<AiMemory.Entity?>(null) }
    var deletingEntity by remember { mutableStateOf<AiMemory.Entity?>(null) }
    var deletingRecipe by remember { mutableStateOf<AiMemory.LearnedRecipe?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        entities = AiMemory.getAllEntities(context)
        recipes = AiMemory.getAllRecipes(context)
    }

    val categories = listOf(
        "all" to "All",
        "contact" to "Contacts",
        "app_default" to "App Defaults",
        "personal" to "Personal",
        "general" to "General",
        "recipes" to "Learned Recipes"
    )

    val filteredEntities = remember(entities, searchQuery, selectedCategory) {
        entities.filter { entity ->
            val matchesCategory = (selectedCategory == "all" || entity.category == selectedCategory)
            val matchesSearch = searchQuery.isBlank() ||
                entity.key.contains(searchQuery, ignoreCase = true) ||
                entity.value.contains(searchQuery, ignoreCase = true) ||
                entity.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val filteredRecipes = remember(recipes, searchQuery) {
        recipes.filter { recipe ->
            searchQuery.isBlank() ||
                recipe.intentPattern.contains(searchQuery, ignoreCase = true) ||
                recipe.targetApp.contains(searchQuery, ignoreCase = true) ||
                recipe.stepsSummary.any { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070A))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = GloomIcons.ChevronLeft,
                    contentDescription = "Back",
                    tint = Apple.systemBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Settings",
                    color = Apple.systemBlue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Add Memory Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E))
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = GloomIcons.Plus,
                            contentDescription = "Add",
                            tint = Apple.systemGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Add",
                            color = Apple.systemGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Reset Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E))
                        .clickable { showResetConfirm = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Reset",
                        color = Apple.systemRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Header Title
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                text = "AI Memories",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Personal relations, default apps, remembered facts & procedural recipes",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search memories, facts, or recipes...", color = Color(0xFF636366), fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C1C1E),
                    unfocusedContainerColor = Color(0xFF1C1C1E),
                    focusedBorderColor = Apple.systemBlue,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                leadingIcon = {
                    Icon(
                        imageVector = GloomIcons.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                item { Spacer(Modifier.width(8.dp)) }
                items(categories) { (id, label) ->
                    val isSelected = selectedCategory == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Apple.systemBlue else Color(0xFF1C1C1E))
                            .clickable { selectedCategory = id }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                item { Spacer(Modifier.width(8.dp)) }
            }
        )

        Spacer(Modifier.height(12.dp))

        // List Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedCategory == "recipes") {
                // Render Recipes Only
                if (filteredRecipes.isEmpty()) {
                    item {
                        EmptyStateCard(
                            message = if (searchQuery.isNotBlank()) "No recipes match \"$searchQuery\""
                            else "No learned action recipes yet. As you automate tasks, GLOOM learns UI paths."
                        )
                    }
                } else {
                    items(filteredRecipes, key = { it.intentPattern }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            onDelete = { deletingRecipe = recipe }
                        )
                    }
                }
            } else {
                // Render Entities
                if (filteredEntities.isEmpty()) {
                    item {
                        EmptyStateCard(
                            message = if (searchQuery.isNotBlank()) "No memories found matching \"$searchQuery\""
                            else "No memories stored in this category. Tap \"Add\" above to save facts!"
                        )
                    }
                } else {
                    items(filteredEntities, key = { it.key }) { entity ->
                        EntityCard(
                            entity = entity,
                            onEdit = { editingEntity = entity },
                            onDelete = { deletingEntity = entity }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    // ── Add Memory Dialog ──
    if (showAddDialog) {
        var keyInput by remember { mutableStateOf("") }
        var valueInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("general") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Add New Memory",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Teach the assistant a personal fact, relationship alias, or preferred app.",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Memory Key (e.g. \"car number\", \"wife\", \"music\")", color = Color(0xFF8E8E93), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Apple.systemBlue,
                            unfocusedBorderColor = Color(0xFF38383A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text("Value (e.g. \"AP05BK1234\", \"Deepika\", \"Harmonix\")", color = Color(0xFF8E8E93), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Apple.systemBlue,
                            unfocusedBorderColor = Color(0xFF38383A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "contact" to "Contact",
                            "app_default" to "Default App",
                            "personal" to "Personal",
                            "general" to "General"
                        ).forEach { (catId, catLabel) ->
                            val sel = categoryInput == catId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) Apple.systemBlue else Color(0xFF2C2C2E))
                                    .clickable { categoryInput = catId }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = catLabel,
                                    color = if (sel) Color.White else Color(0xFF8E8E93),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                            AiMemory.addEntity(context, keyInput, valueInput, categoryInput)
                            showAddDialog = false
                            refreshTick++
                            Toast.makeText(context, "Memory saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Key and value cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save", color = Apple.systemBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color(0xFF8E8E93))
                }
            }
        )
    }

    // ── Edit Memory Dialog ──
    editingEntity?.let { entity ->
        var editKey by remember(entity) { mutableStateOf(entity.key) }
        var editValue by remember(entity) { mutableStateOf(entity.value) }
        var editCategory by remember(entity) { mutableStateOf(entity.category) }

        AlertDialog(
            onDismissRequest = { editingEntity = null },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Edit Memory",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editKey,
                        onValueChange = { editKey = it },
                        label = { Text("Key", color = Color(0xFF8E8E93)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Apple.systemBlue,
                            unfocusedBorderColor = Color(0xFF38383A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text("Value", color = Color(0xFF8E8E93)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Apple.systemBlue,
                            unfocusedBorderColor = Color(0xFF38383A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "contact" to "Contact",
                            "app_default" to "Default App",
                            "personal" to "Personal",
                            "general" to "General"
                        ).forEach { (catId, catLabel) ->
                            val sel = editCategory == catId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) Apple.systemBlue else Color(0xFF2C2C2E))
                                    .clickable { editCategory = catId }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = catLabel,
                                    color = if (sel) Color.White else Color(0xFF8E8E93),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editKey.isNotBlank() && editValue.isNotBlank()) {
                            AiMemory.updateEntity(context, entity.key, editKey, editValue, editCategory)
                            editingEntity = null
                            refreshTick++
                            Toast.makeText(context, "Memory updated!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Update", color = Apple.systemBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntity = null }) {
                    Text("Cancel", color = Color(0xFF8E8E93))
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ──
    deletingEntity?.let { entity ->
        AlertDialog(
            onDismissRequest = { deletingEntity = null },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Delete Memory?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to forget \"${entity.key}\"? The assistant will no longer know this fact.",
                    color = Color(0xFF8E8E93)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AiMemory.deleteEntity(context, entity.key)
                        deletingEntity = null
                        refreshTick++
                        Toast.makeText(context, "Memory deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = Apple.systemRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntity = null }) {
                    Text("Cancel", color = Color(0xFF8E8E93))
                }
            }
        )
    }

    // ── Delete Recipe Dialog ──
    deletingRecipe?.let { recipe ->
        AlertDialog(
            onDismissRequest = { deletingRecipe = null },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Delete Recipe?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete learned recipe for \"${recipe.intentPattern}\"?",
                    color = Color(0xFF8E8E93)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AiMemory.deleteRecipe(context, recipe.intentPattern)
                        deletingRecipe = null
                        refreshTick++
                        Toast.makeText(context, "Recipe removed", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = Apple.systemRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecipe = null }) {
                    Text("Cancel", color = Color(0xFF8E8E93))
                }
            }
        )
    }

    // ── Reset All Memories Dialog ──
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Reset All Memories?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will erase all custom memories, contact aliases, and learned recipes, restoring GLOOM's factory default memory profile (with Harmonix music default).",
                    color = Color(0xFF8E8E93)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AiMemory.clearMemory(context)
                        showResetConfirm = false
                        refreshTick++
                        Toast.makeText(context, "Memory reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset All", color = Apple.systemRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = Color(0xFF8E8E93))
                }
            }
        )
    }
}

@Composable
private fun EntityCard(
    entity: AiMemory.Entity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryBadgeColor = when (entity.category) {
        "contact" -> Apple.systemBlue
        "app_default" -> Apple.systemPurple
        "personal" -> Apple.systemGreen
        else -> Apple.systemOrange
    }

    val categoryLabel = when (entity.category) {
        "contact" -> "Contact"
        "app_default" -> "Default App"
        "personal" -> "Personal"
        else -> "General"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category Capsule
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryBadgeColor.copy(alpha = 0.15f))
                        .border(0.5.dp, categoryBadgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = categoryLabel,
                        color = categoryBadgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Action Icons (Edit & Delete)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = GloomIcons.Pencil,
                        contentDescription = "Edit",
                        tint = Apple.systemBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onEdit() }
                    )
                    Icon(
                        imageVector = GloomIcons.Trash,
                        contentDescription = "Delete",
                        tint = Apple.systemRed,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDelete() }
                    )
                }
            }

            // Key
            Text(
                text = entity.key.replaceFirstChar { it.uppercase() },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2E))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = entity.value,
                    color = Color(0xFFE5E5EA),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Usage & confidence metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Referenced ${entity.usageCount} time(s)",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp
                )
                Text(
                    text = "${(entity.confidence * 100).toInt()}% confidence",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: AiMemory.LearnedRecipe,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Apple.systemTeal.copy(alpha = 0.15f))
                        .border(0.5.dp, Apple.systemTeal.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Recipe • ${recipe.targetApp}",
                        color = Apple.systemTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    imageVector = GloomIcons.Trash,
                    contentDescription = "Delete",
                    tint = Apple.systemRed,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onDelete() }
                )
            }

            Text(
                text = "\"${recipe.intentPattern}\"",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // Steps
            if (recipe.stepsSummary.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    recipe.stepsSummary.forEachIndexed { idx, step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38383A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = step,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${recipe.successCount} successful run(s)",
                    color = Apple.systemGreen,
                    fontSize = 11.sp
                )
                if (recipe.failureCount > 0) {
                    Text(
                        text = "${recipe.failureCount} failure(s)",
                        color = Apple.systemRed,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = GloomIcons.Note,
                contentDescription = null,
                tint = Color(0xFF636366),
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
