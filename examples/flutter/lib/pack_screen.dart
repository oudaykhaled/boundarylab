import 'package:boundarylab/boundarylab.dart';
import 'package:flutter/material.dart';

import 'pack_controller.dart';
import 'theme.dart';

class PackScreen extends StatelessWidget {
  const PackScreen({super.key, required this.controller});
  final PackController controller;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('FieldPack')),
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: PackTokens.maxWidth),
          child: ListenableBuilder(
            listenable: controller,
            builder: (context, _) => PackContents(controller: controller),
          ),
        ),
      ),
    ),
  );
}

class PackContents extends StatelessWidget {
  const PackContents({super.key, required this.controller});
  final PackController controller;
  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(PackTokens.page),
    children: [
      const IntroCard(),
      const SizedBox(height: PackTokens.medium),
      PackActions(controller: controller),
      const SizedBox(height: PackTokens.medium),
      PackStatus(controller: controller),
      for (final selection in controller.selections)
        AttachmentTile(selection: selection, entries: controller.entries),
      const TransferNotice(),
      SessionHistory(controller: controller),
      GeneratedPanel(controller: controller),
    ],
  );
}

class IntroCard extends StatelessWidget {
  const IntroCard({super.key});
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        'Prepare a reliable handoff',
        style: Theme.of(context).textTheme.headlineSmall,
      ),
      const SizedBox(height: PackTokens.small),
      const Text('Inspection handoff'),
      const Text(
        '1 Select attachments   ·   2 Prepare pack   ·   3 Export manifest',
      ),
      const SizedBox(height: PackTokens.small),
      const Text(
        'Up to 32 local files · 128 MiB total. Use stable files that are not being edited. Some document providers are unsupported.',
      ),
    ],
  );
}

class PackActions extends StatelessWidget {
  const PackActions({super.key, required this.controller});
  final PackController controller;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      OutlinedButton.icon(
        key: const Key('select_attachments'),
        onPressed: controller.busy ? null : controller.select,
        icon: const Icon(Icons.add),
        label: const Text('Select attachments'),
      ),
      const SizedBox(height: PackTokens.small),
      FilledButton(
        key: const Key('prepare_pack'),
        onPressed: controller.busy || controller.selections.isEmpty
            ? null
            : controller.prepare,
        child: const Text('Prepare pack'),
      ),
      const SizedBox(height: PackTokens.small),
      FilledButton.icon(
        key: const Key('export_manifest'),
        onPressed: controller.canExport ? controller.export : null,
        icon: const Icon(Icons.save_alt),
        label: const Text('Export manifest'),
      ),
      if (controller.phase == PackPhase.preparing)
        TextButton(
          key: const Key('cancel_attempt'),
          onPressed: controller.cancel,
          child: const Text('Cancel attempt'),
        ),
    ],
  );
}

class PackStatus extends StatelessWidget {
  const PackStatus({super.key, required this.controller});
  final PackController controller;
  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(PackTokens.medium),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(controller.message, key: const Key('pack_status')),
            if (controller.busy)
              const LinearProgressIndicator(
                semanticsLabel: 'Operation in progress',
              ),
            if (controller.progressLabel.isNotEmpty)
              Text(controller.progressLabel),
          ],
        ),
      ),
    ),
  );
}

class AttachmentTile extends StatelessWidget {
  const AttachmentTile({
    super.key,
    required this.selection,
    required this.entries,
  });
  final Selection selection;
  final List<DigestEntry> entries;
  @override
  Widget build(BuildContext context) {
    final completed = entries
        .where((entry) => entry.alias == selection.alias)
        .firstOrNull;
    return Card(
      child: ListTile(
        leading: Icon(
          completed == null
              ? Icons.description_outlined
              : Icons.check_circle_outline,
        ),
        title: Text(selection.alias, textDirection: TextDirection.ltr),
        subtitle: Text(
          '${selection.displayName ?? "Selected file"} → ${selection.alias}\n'
          '${completed == null ? "Waiting for this attempt" : "${completed.size} bytes · SHA-256 checked"}',
        ),
        trailing: completed == null
            ? null
            : const Icon(Icons.done, semanticLabel: 'Checked'),
      ),
    );
  }
}

class TransferNotice extends StatelessWidget {
  const TransferNotice({super.key});
  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(vertical: PackTokens.medium),
    child: Text(
      'The manifest contains checksums, not attachments. Transfer files separately and name them exactly as the aliases shown above. Aliases do not rename the originals. Checksums compare bytes; they do not prove who sent the files.',
    ),
  );
}

class GeneratedPanel extends StatelessWidget {
  const GeneratedPanel({super.key, required this.controller});
  final PackController controller;
  @override
  Widget build(BuildContext context) => ExpansionTile(
    key: const Key('generated_diagnostics'),
    title: const Text('Boundary diagnostics'),
    subtitle: const Text('Generated data · separate from your attachments'),
    children: [
      if (controller.diagnosticCode != null)
        Text('Diagnostic: ${controller.diagnosticCode}'),
      Padding(
        padding: const EdgeInsets.all(PackTokens.medium),
        child: OutlinedButton(
          key: const Key('run_generated'),
          onPressed: controller.busy ? null : controller.generated,
          child: const Text('Run generated workload'),
        ),
      ),
      if (controller.generatedDigest != null)
        SelectableText(
          controller.generatedDigest!,
          key: const Key('generated_digest'),
        ),
    ],
  );
}

class SessionHistory extends StatelessWidget {
  const SessionHistory({super.key, required this.controller});
  final PackController controller;
  @override
  Widget build(BuildContext context) => ExpansionTile(
    key: const Key('session_history'),
    title: const Text('This session'),
    subtitle: const Text(
      'Cleared when the app restarts. Previous attempts cannot be exported.',
    ),
    children: [
      if (controller.history.isEmpty)
        const ListTile(title: Text('No completed attempts yet.')),
      for (final entry in controller.history) ListTile(title: Text(entry)),
    ],
  );
}
