declare module 'turndown' {
  export interface TurndownOptions {
    headingStyle?: 'setext' | 'atx'
    hr?: string
    bulletListMarker?: '-' | '*' | '+'
    codeBlockStyle?: 'indented' | 'fenced'
    emDelimiter?: '_' | '*'
    strongDelimiter?: '**' | '__'
  }

  export default class TurndownService {
    constructor(options?: TurndownOptions)
    turndown(input: string): string
  }
}
