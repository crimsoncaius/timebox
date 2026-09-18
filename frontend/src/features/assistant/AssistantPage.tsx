import { Layout } from '../../components/Layout'

/** Placeholder home for the Assistant until its conversations land (#9). */
export function AssistantPage() {
  return (
    <Layout>
      <section className="mb-10 max-w-2xl">
        <h1 className="mb-2 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
          Assistant
        </h1>
      </section>
      <div className="max-w-xl rounded-xl border border-outline-variant/30 bg-surface-container-low/80 px-4 py-3 dark:border-dark-outline-variant dark:bg-dark-surface-container">
        <p className="text-sm font-medium text-on-surface dark:text-dark-on-surface">Assistant is on its way</p>
        <p className="mt-1 text-sm text-on-surface-variant dark:text-dark-on-surface-variant">
          Conversations with the assistant will live here.
        </p>
      </div>
    </Layout>
  )
}
