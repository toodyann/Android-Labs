import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

void main() {
  runApp(const NotesApp());
}

class NotesApp extends StatelessWidget {
  const NotesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Notes App',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: const NotesPage(),
    );
  }
}

class Note {
  final int? id;
  final String title;
  final String description;
  final DateTime createdAt;

  Note({
    this.id,
    required this.title,
    required this.description,
    DateTime? createdAt,
  }) : createdAt = createdAt ?? DateTime.now();

  Map<String, Object?> toMap() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'createdAt': createdAt.toIso8601String(),
    };
  }

  factory Note.fromMap(Map<String, Object?> map) {
    return Note(
      id: map['id'] as int?,
      title: map['title'] as String? ?? '',
      description: map['description'] as String? ?? '',
      createdAt: DateTime.parse(
        map['createdAt'] as String? ?? DateTime.now().toIso8601String(),
      ),
    );
  }
}

class DatabaseHelper {
  DatabaseHelper._privateConstructor();
  static final DatabaseHelper instance = DatabaseHelper._privateConstructor();

  static Database? _database;

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    final dbPath = await getDatabasesPath();
    final databasePath = path.join(dbPath, 'notes_app.db');

    return await openDatabase(
      databasePath,
      version: 1,
      onCreate: (db, version) {
        return db.execute('''
          CREATE TABLE notes(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            createdAt TEXT NOT NULL
          )
        ''');
      },
    );
  }

  Future<List<Note>> getNotes() async {
    final db = await database;
    final rows = await db.query('notes', orderBy: 'createdAt DESC');
    return rows.map((row) => Note.fromMap(row)).toList();
  }

  Future<int> insertNote(Note note) async {
    final db = await database;
    return await db.insert('notes', note.toMap());
  }

  Future<void> deleteNote(int id) async {
    final db = await database;
    await db.delete('notes', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> insertNotes(List<Note> notes) async {
    final db = await database;
    final batch = db.batch();
    for (final note in notes) {
      batch.insert('notes', note.toMap());
    }
    await batch.commit(noResult: true);
  }
}

class NotesPage extends StatefulWidget {
  const NotesPage({super.key});

  @override
  State<NotesPage> createState() => _NotesPageState();
}

class _NotesPageState extends State<NotesPage> {
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  final _dbHelper = DatabaseHelper.instance;

  List<Note> _notes = [];
  bool _isLoading = false;
  bool _isFetching = false;

  @override
  void initState() {
    super.initState();
    _loadNotes();
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _loadNotes() async {
    setState(() {
      _isLoading = true;
    });

    final notes = await _dbHelper.getNotes();

    setState(() {
      _notes = notes;
      _isLoading = false;
    });
  }

  Future<void> _addNote() async {
    if (!_formKey.currentState!.validate()) return;

    final newNote = Note(
      title: _titleController.text.trim(),
      description: _descriptionController.text.trim(),
    );

    await _dbHelper.insertNote(newNote);
    _titleController.clear();
    _descriptionController.clear();
    if (mounted) {
      Navigator.of(context).pop();
      await _loadNotes();
    }
  }

  Future<void> _deleteNote(Note note) async {
    if (note.id != null) {
      await _dbHelper.deleteNote(note.id!);
      await _loadNotes();
    }
  }

  Future<void> _fetchSampleNotes() async {
    setState(() {
      _isFetching = true;
    });

    try {
      final response = await http.get(
        Uri.parse('https://jsonplaceholder.typicode.com/posts?_limit=5'),
      );

      if (response.statusCode == 200) {
        final List<dynamic> payload =
            jsonDecode(response.body) as List<dynamic>;
        final sampleNotes = payload.map((item) {
          final map = item as Map<String, dynamic>;
          return Note(
            title: map['title'] as String? ?? 'Без назви',
            description: map['body'] as String? ?? '',
          );
        }).toList();

        await _dbHelper.insertNotes(sampleNotes);
        await _loadNotes();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Семпл нотатки завантажено та збережено.'),
            ),
          );
        }
      } else {
        _showError('Не вдалося завантажити дані: ${response.statusCode}');
      }
    } catch (error) {
      _showError('Помилка HTTP-запиту: $error');
    } finally {
      if (mounted) {
        setState(() {
          _isFetching = false;
        });
      }
    }
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  void _showAddNoteDialog() {
    showDialog<void>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Нова нотатка'),
          content: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  controller: _titleController,
                  decoration: const InputDecoration(labelText: 'Назва'),
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'Введіть назву';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _descriptionController,
                  decoration: const InputDecoration(labelText: 'Опис'),
                  minLines: 2,
                  maxLines: 4,
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'Введіть опис';
                    }
                    return null;
                  },
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () {
                _titleController.clear();
                _descriptionController.clear();
                Navigator.of(dialogContext).pop();
              },
              child: const Text('Скасувати'),
            ),
            ElevatedButton(onPressed: _addNote, child: const Text('Додати')),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notes App'),
        actions: [
          IconButton(
            onPressed: _isFetching ? null : _fetchSampleNotes,
            icon: _isFetching
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.cloud_download),
            tooltip: 'Завантажити приклади',
          ),
          IconButton(
            onPressed: _loadNotes,
            icon: const Icon(Icons.refresh),
            tooltip: 'Оновити список',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _notes.isEmpty
          ? Padding(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.note_alt_outlined,
                    size: 80,
                    color: Colors.indigo,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Список нотаток порожній.\nДодайте нову нотатку або завантажте приклади.',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 18),
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton.icon(
                    onPressed: _isFetching ? null : _fetchSampleNotes,
                    icon: const Icon(Icons.cloud_download),
                    label: const Text('Завантажити приклади'),
                  ),
                ],
              ),
            )
          : ListView.separated(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: _notes.length,
              separatorBuilder: (context, index) => const Divider(height: 0),
              itemBuilder: (context, index) {
                final note = _notes[index];
                return Dismissible(
                  key: ValueKey(
                    note.id ?? note.title + note.createdAt.toIso8601String(),
                  ),
                  direction: DismissDirection.endToStart,
                  background: Container(
                    color: Colors.red,
                    alignment: Alignment.centerRight,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: const Icon(Icons.delete, color: Colors.white),
                  ),
                  onDismissed: (_) async {
                    final messenger = ScaffoldMessenger.of(context);
                    await _deleteNote(note);
                    if (mounted) {
                      messenger.showSnackBar(
                        const SnackBar(content: Text('Нотатку видалено')),
                      );
                    }
                  },
                  child: ListTile(
                    title: Text(note.title),
                    subtitle: Text(note.description),
                    trailing: Text(
                      '${note.createdAt.day}.${note.createdAt.month}.${note.createdAt.year}',
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showAddNoteDialog,
        tooltip: 'Додати нотатку',
        child: const Icon(Icons.add),
      ),
    );
  }
}
